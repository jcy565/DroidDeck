package com.droiddeck.launcher.files

import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImagePatchArchiveTest {
    @get:Rule val temp = TemporaryFolder()
    private val source = ImagePatchArchive.Source("ui/menu.png", "a".repeat(64), 3, 32, 32)

    @Test fun createsVerifiedArchiveWithoutGameWrites() {
        val game = temp.newFolder()
        val out = temp.newFolder()
        val bytes = byteArrayOf(1, 2, 3)
        val patch = ImagePatchArchive.create(game, out, listOf(source), { bytes })
        assertEquals(patch.rows, ImagePatchArchive.verify(patch.file))
        assertEquals(patch.digest, ImagePatchArchive.digest(patch.file))
        ZipFile(patch.file).use { zip ->
            assertArrayEquals(bytes, zip.getInputStream(zip.getEntry("files/ui/menu.png")).readBytes())
        }
        assertEquals(0, game.listFiles()!!.size)
    }

    @Test fun largePayloadCanBeCancelledBeforeVisitorAndImportCleansCopy() {
        val game = temp.newFolder()
        val out = temp.newFolder()
        val input = ByteArray(512 * 1024) { 7 }
        val patch = ImagePatchArchive.create(game, out, listOf(source), { input })
        var checks = 0
        var visited = false
        val failure = runCatching {
            ImagePatchArchive.inspectPayloads(patch.file, {
                if (++checks == 10) throw java.util.concurrent.CancellationException()
            }) { _, _ -> visited = true }
        }.exceptionOrNull()
        assertTrue(failure is java.util.concurrent.CancellationException)
        assertFalse(visited)
        assertEquals(patch.rows, ImagePatchArchive.verify(patch.file))

        val staging = temp.newFolder()
        var allowed = true
        val stream = object : java.io.ByteArrayInputStream(patch.file.readBytes()) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = super.read(b, off, len)
                if (n == -1) allowed = false
                return n
            }
        }
        assertTrue(runCatching {
            ImagePatchImport.copyAndInspect(game, staging, stream, { _, _ -> }) {
                if (!allowed) throw java.util.concurrent.CancellationException()
            }
        }.exceptionOrNull() is java.util.concurrent.CancellationException)
        assertTrue(staging.listFiles()!!.isEmpty())
        assertTrue(game.listFiles()!!.isEmpty())
    }

    @Test fun rejectsInvalidPathsAndChangedApproval() {
        val game = temp.newFolder()
        val out = temp.newFolder()
        for (path in listOf("../menu.png", "/menu.png", "C:/menu.png", "NUL.png", "menu.jpg")) {
            assertTrue(runCatching { ImagePatchArchive.create(game, out, listOf(source.copy(relative = path)), { byteArrayOf(1) }) }.isFailure)
        }
        assertTrue(runCatching { ImagePatchArchive.create(game, out,
            listOf(source, source.copy(relative = "UI/MENU.PNG")), { byteArrayOf(1) }) }.isFailure)
        var calls = 0
        assertTrue(runCatching { ImagePatchArchive.create(game, out, listOf(source), { byteArrayOf((++calls).toByte()) }) }.isFailure)
        assertTrue(runCatching { ImagePatchArchive.create(game, File(game, "output"), listOf(source), { byteArrayOf(1) }) }.isFailure)
        assertEquals(0, out.listFiles()!!.size)
        assertEquals(0, game.listFiles()!!.size)
    }
}
