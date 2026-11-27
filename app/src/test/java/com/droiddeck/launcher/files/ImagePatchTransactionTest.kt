package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImagePatchTransactionTest {
    @get:Rule val temp = TemporaryFolder()
    private val verify: (ByteArray, ImagePatchArchive.Source) -> Unit = { _, _ -> }
    private val original = byteArrayOf(1, 2, 3)
    private val translated = byteArrayOf(4, 5, 6, 7)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private inner class Fixture {
        val root = temp.newFolder()
        val storage = temp.newFolder()
        val a = File(root, "a.png").apply { writeBytes(original) }
        val b = File(root, "b.dds").apply { writeBytes(original) }
        val patch = ImagePatchArchive.create(root, temp.newFolder(),
            listOf(a, b).map { ImagePatchArchive.Source(it.name, hash(original), 3, 32, 32) }, { translated })
        val backup = ImagePatchBackup.create(storage, root, ImagePatchImport.inspect(root, patch.file, verify), verify)
        fun apply(checkpoint: () -> Unit = {}) = ImagePatchTransaction.apply(storage, root, patch.file, verify, checkpoint)
        fun recover() = ImagePatchTransaction.recover(storage, root)
        fun status() = ImagePatchTransaction.status(storage, root)
    }

    @Test fun appliesAndRestoresWithoutOverwritingUnknownRevision() {
        val f = Fixture()
        assertEquals(ImagePatchTransaction.Phase.APPLIED, f.apply().phase)
        assertArrayEquals(translated, f.a.readBytes())
        assertArrayEquals(translated, f.b.readBytes())
        f.b.writeBytes(byteArrayOf(9))
        val recovery = f.recover()
        assertEquals(1, recovery.restored)
        assertEquals(listOf("b.dds"), recovery.conflicts)
        assertArrayEquals(original, f.a.readBytes())
        assertArrayEquals(byteArrayOf(9), f.b.readBytes())
        assertEquals(ImagePatchTransaction.Phase.APPLIED, f.status().phase)
    }

    @Test fun rollbackAndInterruptedRecovery() {
        val f = Fixture()
        var thrown = false
        assertTrue(runCatching { f.apply {
            if (!thrown && f.a.readBytes().contentEquals(translated)) {
                thrown = true
                error("injected failure")
            }
        } }.isFailure)
        assertTrue(thrown)
        assertArrayEquals(original, f.a.readBytes())
        assertEquals(ImagePatchTransaction.Phase.RESTORED, f.status().phase)
        var cancelled = false
        assertTrue(runCatching { f.apply {
            if (cancelled || f.a.readBytes().contentEquals(translated)) {
                cancelled = true
                throw CancellationException()
            }
        } }.isFailure)
        assertEquals(ImagePatchTransaction.Phase.APPLYING, f.status().phase)
        assertEquals(1, f.recover().restored)
        assertArrayEquals(original, f.a.readBytes())
        assertEquals(ImagePatchTransaction.Phase.RESTORED, f.status().phase)
    }
}
