package com.droiddeck.launcher.files

import com.droiddeck.ai.core.GlossaryStore
import com.droiddeck.ai.core.SignedGlossary
import com.droiddeck.launcher.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Explicit local data import only; the trust root belongs to the installed APK. */
internal object AiGlossaryUpdates {
    val configured: Boolean get() = BuildConfig.AI_DATA_PUBLIC_KEY.isNotBlank()

    internal fun key(): PublicKey {
        check(configured) { "AI 데이터 서명 키가 등록되지 않은 빌드입니다." }
        return KeyFactory.getInstance("RSA").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(BuildConfig.AI_DATA_PUBLIC_KEY)))
    }

    fun active(directory: File): SignedGlossary.Bundle? {
        if (!configured) return null
        AiHostBridge.requireCompatible()
        return GlossaryStore.active(directory.toPath(), key(), AiHostBridge.HOST_API)
    }

    fun install(directory: File, input: InputStream, allowed: () -> Boolean): SignedGlossary.Bundle {
        AiHostBridge.requireCompatible()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            check(allowed())
            val n = input.read(buffer)
            if (n < 0) break
            require(n > 0 && output.size() + n <= SignedGlossary.MAX_BUNDLE) { "AI 데이터 크기 초과" }
            output.write(buffer, 0, n)
        }
        return GlossaryStore.install(directory.toPath(), output.toByteArray(), key(),
            AiHostBridge.HOST_API) { allowed() }
    }

    fun rollback(directory: File, allowed: () -> Boolean): SignedGlossary.Bundle {
        AiHostBridge.requireCompatible()
        return GlossaryStore.rollback(directory.toPath(), key(), AiHostBridge.HOST_API) { allowed() }
    }
}
