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
