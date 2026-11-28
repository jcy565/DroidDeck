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

    @Test fun retirementProtectsActiveModelAndAllowsNextRevision() {
        val dir = folder.newFolder().toPath()
        val m1 = manifest(1, first)
        val m2 = manifest(2, second)
        val third = byteArrayOf(8, 9)
        ModelRelease.install(dir, m1, first.inputStream(), key.public, 1, 1) { true }
        assertTrue(ModelRelease.retirementCandidates(dir, key.public) { true }.isEmpty())
        ModelRelease.install(dir, m2, second.inputStream(), key.public, 1, 1) { true }
        val reviewed = ModelRelease.retirementCandidates(dir, key.public) { true }.single()
        assertEquals(1, reviewed.previousRevision)
        ModelRelease.rollback(dir, key.public, 1, 1) { true }
        assertTrue(runCatching { ModelRelease.retire(dir, reviewed, key.public) { true } }.isFailure)
        ModelRelease.rollback(dir, key.public, 1, 1) { true }
        val fresh = ModelRelease.retirementCandidates(dir, key.public) { true }.single()
        val result = ModelRelease.retire(dir, fresh, key.public) { true }
        assertTrue(result.rollbackRemoved)
        assertTrue(result.fileDeleted)
        assertEquals(2, ModelRelease.active(dir, key.public, 1, 1)!!.revision)
        assertTrue(runCatching { ModelRelease.rollback(dir, key.public, 1, 1) { true } }.isFailure)
        ModelRelease.install(dir, manifest(3, third), third.inputStream(), key.public, 1, 1) { true }
        assertEquals(3, ModelRelease.active(dir, key.public, 1, 1)!!.revision)
        assertEquals(2, ModelRelease.retirementCandidates(dir, key.public) { true }.single().previousRevision)
    }

    @Test fun cancellationAtModelEofPreservesPreviousState() {
        val dir = folder.newFolder().toPath()
        ModelRelease.install(dir, manifest(1, first), first.inputStream(), key.public, 1, 1) { true }
        val before = dir.resolve("models.state").toFile().readBytes()
        var allowed = true
        val stream = object : java.io.ByteArrayInputStream(second) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n == -1) allowed = false
                return n
            }
        }
        assertTrue(runCatching {
            ModelRelease.install(dir, manifest(2, second), stream, key.public, 1, 1) { allowed }
        }.isFailure)
        assertArrayEquals(before, dir.resolve("models.state").toFile().readBytes())
        assertEquals(2, dir.toFile().listFiles()!!.size)
        assertArrayEquals(first, ModelRelease.verifiedActive(dir, key.public, 1, 1) { true }.toFile().readBytes())
    }

    @Test fun changedRetirementFileRequiresFreshReview() {
        val dir = folder.newFolder().toPath()
        ModelRelease.install(dir, manifest(1, first), first.inputStream(), key.public, 1, 1) { true }
        ModelRelease.install(dir, manifest(2, second), second.inputStream(), key.public, 1, 1) { true }
        val reviewed = ModelRelease.retirementCandidates(dir, key.public) { true }.single()
        val candidate = dir.resolve(reviewed.sha256 + ".task")
        val before = dir.resolve("models.state").toFile().readBytes()
        val modified = java.nio.file.Files.getLastModifiedTime(candidate)
        candidate.toFile().writeBytes(byteArrayOf(9, 9, 9))
        java.nio.file.Files.setLastModifiedTime(candidate, java.nio.file.attribute.FileTime.fromMillis(modified.toMillis() + 10000))
        assertTrue(runCatching { ModelRelease.retire(dir, reviewed, key.public) { true } }.isFailure)
        assertArrayEquals(before, dir.resolve("models.state").toFile().readBytes())
        assertArrayEquals(byteArrayOf(9, 9, 9), candidate.toFile().readBytes())
        assertArrayEquals(second, ModelRelease.verifiedActive(dir, key.public, 1, 1) { true }.toFile().readBytes())
    }

    @Test fun invalidModelDirectoryIsNotReportedAsEmpty() {
        val dir = folder.newFile().toPath()
        assertTrue(runCatching { ModelRelease.active(dir, key.public, 1, 1) }.isFailure)
        assertTrue(runCatching { ModelRelease.retirementCandidates(dir, key.public) { true } }.isFailure)
    }

    @Test fun interruptedRetirementLeavesReviewableOrphan() {
        val dir = folder.newFolder().toPath()
        ModelRelease.install(dir, manifest(1, first), first.inputStream(), key.public, 1, 1) { true }
        ModelRelease.install(dir, manifest(2, second), second.inputStream(), key.public, 1, 1) { true }
        val reviewed = ModelRelease.retirementCandidates(dir, key.public) { true }.single()
        var calls = 0
        val result = ModelRelease.retire(dir, reviewed, key.public) { ++calls < 4 }
        assertTrue(result.rollbackRemoved)
        assertFalse(result.fileDeleted)
        val orphan = ModelRelease.retirementCandidates(dir, key.public) { true }.single()
        assertEquals(0, orphan.previousRevision)
        assertTrue(ModelRelease.retire(dir, orphan, key.public) { true }.fileDeleted)
        assertEquals(2, ModelRelease.active(dir, key.public, 1, 1)!!.revision)
    }
}
