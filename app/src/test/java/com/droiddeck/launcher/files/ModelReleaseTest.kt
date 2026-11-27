package com.droiddeck.launcher.files

import com.droiddeck.ai.core.ModelRelease
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelReleaseTest {
    @get:Rule val folder = TemporaryFolder()
    private val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val first = byteArrayOf(1, 2, 3)
    private val second = byteArrayOf(4, 5, 6, 7)
    private fun manifest(revision: Int, data: ByteArray, min: Int = 1, max: Int = 1,
        runtime: Int = 1, signer: KeyPair = key): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        listOf(0x4444414d, 1, revision, min, max, runtime).forEach(out::writeInt)
        out.writeLong(data.size.toLong())
        out.write(MessageDigest.getInstance("SHA-256").digest(data))
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(signer.private); update(bytes.toByteArray())
        }.sign()
        out.writeInt(sig.size); out.write(sig)
        return bytes.toByteArray()
    }
    @Test fun signedModelActivationAndRollback() {
        val dir = folder.newFolder().toPath()
        val m1 = manifest(1, first, max = 2)
        val m2 = manifest(2, second)
        assertNull(ModelRelease.active(dir, key.public, 1, 1))
        ModelRelease.install(dir, m1, first.inputStream(), key.public, 1, 1) { true }
        ModelRelease.install(dir, m2, second.inputStream(), key.public, 1, 1) { true }
        assertArrayEquals(second, ModelRelease.verifiedActive(dir, key.public, 1, 1) { true }.toFile().readBytes())
        assertNull(ModelRelease.active(dir, key.public, 2, 1))
        assertEquals(1, ModelRelease.rollback(dir, key.public, 2, 1) { true }.revision)
        assertArrayEquals(first, ModelRelease.verifiedActive(dir, key.public, 2, 1) { true }.toFile().readBytes())
        assertTrue(runCatching { ModelRelease.install(dir, m2, second.inputStream(), key.public, 1, 1) { true } }.isFailure)
    }

    @Test fun invalidAndInterruptedUpdatesPreserveCurrentModel() {
        val dir = folder.newFolder().toPath()
        val m1 = manifest(1, first)
        val m2 = manifest(2, second)
        ModelRelease.install(dir, m1, first.inputStream(), key.public, 1, 1) { true }
        val other = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertTrue(runCatching { ModelRelease.verify(manifest(2, second, signer = other), key.public) }.isFailure)
        assertTrue(runCatching { ModelRelease.install(dir, m2, first.inputStream(), key.public, 1, 1) { true } }.isFailure)
        assertTrue(runCatching { ModelRelease.install(dir, m2, second.inputStream(), key.public, 1, 2) { true } }.isFailure)
        var calls = 0
        assertTrue(runCatching { ModelRelease.install(dir, m2, second.inputStream(), key.public, 1, 1) { ++calls < 7 } }.isFailure)
        assertEquals(1, ModelRelease.active(dir, key.public, 1, 1)!!.revision)
        ModelRelease.install(dir, m2, second.inputStream(), key.public, 1, 1) { true }
        assertTrue(runCatching { ModelRelease.install(dir, manifest(3, byteArrayOf(8)), byteArrayOf(8).inputStream(), key.public, 1, 1) { true } }.isFailure)
    }
}
