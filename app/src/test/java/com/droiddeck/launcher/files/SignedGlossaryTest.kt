package com.droiddeck.launcher.files

import com.droiddeck.ai.core.GlossaryStore
import com.droiddeck.ai.core.SignedGlossary
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignedGlossaryTest {
    @get:Rule val temp = TemporaryFolder()
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun bundle(rev: Int, min: Int = 1, max: Int = 1, text: String = "back\t뒤로",
        signer: KeyPair = key): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        val payload = text.toByteArray(Charsets.UTF_8)
        listOf(0x44444147, 1, rev, min, max, payload.size).forEach(out::writeInt)
        out.write(payload)
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(signer.private); update(bytes.toByteArray())
        }.sign()
        out.writeInt(sig.size); out.write(sig)
        return bytes.toByteArray()
    }

    @Test fun signatureAndPayloadAreVerified() {
        val one = bundle(1)
        assertEquals("뒤로", SignedGlossary.verify(one, key.public).entries["back"])
        val wrong = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        for (invalid in listOf(one.clone().apply { this[25] = (this[25].toInt() xor 1).toByte() },
            one + byteArrayOf(0), bundle(1, signer = wrong), bundle(1, text = "back\t뒤로\nback\t이전"),
            bundle(1, text = "back\t{value}"))) {
            assertTrue(runCatching { SignedGlossary.verify(invalid, key.public) }.isFailure)
        }
    }

    @Test fun rollbackAndHighestRevisionSurviveReopen() {
        val path = temp.newFolder().toPath()
        assertNull(GlossaryStore.active(path, key.public, 1))
        GlossaryStore.install(path, bundle(1, max = 2), key.public, 1) { true }
        GlossaryStore.install(path, bundle(2), key.public, 1) { true }
        assertNull(GlossaryStore.active(path, key.public, 2))
        assertEquals(1, GlossaryStore.rollback(path, key.public, 2) { true }.revision)
        assertTrue(runCatching { GlossaryStore.install(path, bundle(2), key.public, 1) { true } }.isFailure)
        assertEquals(1, GlossaryStore.active(path, key.public, 2)!!.revision)
    }

    @Test fun blockedOrIncompatibleUpdatePreservesState() {
        val path = temp.newFolder().toPath()
        GlossaryStore.install(path, bundle(1), key.public, 1) { true }
        assertTrue(runCatching { GlossaryStore.install(path, bundle(2), key.public, 2) { true } }.isFailure)
        var calls = 0
        assertTrue(runCatching { GlossaryStore.install(path, bundle(2), key.public, 1) { ++calls < 3 } }.isFailure)
        assertEquals(1, GlossaryStore.active(path, key.public, 1)!!.revision)
    }
}
