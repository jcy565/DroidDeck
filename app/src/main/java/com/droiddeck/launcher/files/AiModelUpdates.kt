package com.droiddeck.launcher.files

import com.droiddeck.ai.core.ModelRelease
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Only the pinned APK trust key authorizes managed model data. */
internal object AiModelUpdates {
    const val RUNTIME_API = 1 // Experimental MediaPipe .task contract, not a model-format proof.
    data class Pending(val encoded: ByteArray, val manifest: ModelRelease.Manifest)

    fun inspect(input: InputStream, allowed: () -> Boolean): Pending {
        AiHostBridge.requireCompatible()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            check(allowed())
            val n = input.read(buffer)
            if (n < 0) break
            require(n > 0 && out.size() + n <= ModelRelease.MAX_MANIFEST) { "모델 명세 크기 초과" }
            out.write(buffer, 0, n)
        }
        val bytes = out.toByteArray()
        val manifest = ModelRelease.verify(bytes, AiGlossaryUpdates.key())
        require(manifest.compatible(AiHostBridge.HOST_API, RUNTIME_API)) { "엔진 또는 모델 런타임 버전이 호환되지 않습니다." }
        return Pending(bytes, manifest)
    }

    fun active(directory: File): ModelRelease.Manifest? {
        AiHostBridge.requireCompatible()
        return ModelRelease.active(directory.toPath(), AiGlossaryUpdates.key(), AiHostBridge.HOST_API, RUNTIME_API)
    }

    fun install(directory: File, pending: Pending, input: InputStream, allowed: () -> Boolean): ModelRelease.Manifest {
        AiHostBridge.requireCompatible()
        return ModelRelease.install(directory.toPath(), pending.encoded, input, AiGlossaryUpdates.key(),
            AiHostBridge.HOST_API, RUNTIME_API) { allowed() }
    }

    fun verifiedActive(directory: File, allowed: () -> Boolean): File {
        AiHostBridge.requireCompatible()
        return ModelRelease.verifiedActive(directory.toPath(), AiGlossaryUpdates.key(),
            AiHostBridge.HOST_API, RUNTIME_API) { allowed() }.toFile()
    }

    fun rollback(directory: File, allowed: () -> Boolean): ModelRelease.Manifest {
        AiHostBridge.requireCompatible()
        return ModelRelease.rollback(directory.toPath(), AiGlossaryUpdates.key(),
            AiHostBridge.HOST_API, RUNTIME_API) { allowed() }
    }
}
