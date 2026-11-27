package com.droiddeck.launcher.files

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImagePatchBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun retainsExactOriginalsAndRefusesOverwrite() {
        val root = temp.newFolder()
        val out = temp.newFolder()
        val storage = temp.newFolder()
        val bytes = byteArrayOf(1, 2, 3)
        val file = File(root, "menu.png").apply { writeBytes(bytes) }
        val source = ImagePatchArchive.Source(file.name, hash(bytes), 3, 32, 32)
        val patch = ImagePatchArchive.create(root, out, listOf(source), { byteArrayOf(4, 5, 6) })
        val verify: (ByteArray, ImagePatchArchive.Source) -> Unit = { _, _ -> }
        val report = ImagePatchImport.inspect(root, patch.file, verify)
        val saved = ImagePatchBackup.create(storage, root, report, verify)
        assertEquals(saved, ImagePatchBackup.load(storage, root))
        assertArrayEquals(bytes, file.readBytes())
        assertTrue(runCatching { ImagePatchBackup.create(storage, root, report, verify) }.isFailure)
        patch.file.delete()
        file.writeBytes(byteArrayOf(9))
        assertEquals(saved, ImagePatchBackup.load(storage, root))
        File(saved.directory, "recovery.ddib").appendBytes(byteArrayOf(0))
        assertTrue(runCatching { ImagePatchBackup.load(storage, root) }.isFailure)
    }

    @Test fun refusesChangedSourceAndDoesNotCreateBackupInsideGame() {
        val root = temp.newFolder()
        val out = temp.newFolder()
        val storage = temp.newFolder()
        val bytes = byteArrayOf(1, 2, 3)
        val file = File(root, "menu.png").apply { writeBytes(bytes) }
        val patch = ImagePatchArchive.create(root, out,
            listOf(ImagePatchArchive.Source(file.name, hash(bytes), 3, 32, 32)), { byteArrayOf(4) })
        val verify: (ByteArray, ImagePatchArchive.Source) -> Unit = { _, _ -> }
        val report = ImagePatchImport.inspect(root, patch.file, verify)
        file.writeBytes(byteArrayOf(9))
        assertTrue(runCatching { ImagePatchBackup.create(storage, root, report, verify) }.isFailure)
        assertEquals(0, storage.listFiles()!!.size)
        val nested = File(root, "backup")
        assertTrue(runCatching { ImagePatchBackup.create(nested, root, report, verify) }.isFailure)
        assertFalse(nested.exists())
    }
}
