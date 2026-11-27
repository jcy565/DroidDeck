package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImagePatchImportTest {
    @get:Rule val temp = TemporaryFolder()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun checksCurrentSourceWithoutExtractingOrWritingGameFiles() {
        val game = temp.newFolder()
        val output = temp.newFolder()
        val cache = temp.newFolder()
        val original = byteArrayOf(1, 2, 3)
        val translated = byteArrayOf(4, 5, 6)
        val names = listOf("ready.png", "applied.png", "missing.png", "changed.png", "link.png")
        File(game, names[0]).writeBytes(original)
        File(game, names[1]).writeBytes(translated)
        File(game, names[3]).writeBytes(byteArrayOf(9, 9, 9))
        Files.createSymbolicLink(File(game, names[4]).toPath(), File(game, names[0]).toPath())
        val sources = names.map { ImagePatchArchive.Source(it, hash(original), 3, 32, 32) }
        val patch = ImagePatchArchive.create(game, output, sources, { translated })
        val report = patch.file.inputStream().use {
            ImagePatchImport.copyAndInspect(game, cache, it, verify = { _, _ -> })
        }
        assertEquals(listOf(ImagePatchImport.State.READY, ImagePatchImport.State.ALREADY_APPLIED,
            ImagePatchImport.State.MISSING, ImagePatchImport.State.MISMATCH, ImagePatchImport.State.UNSAFE),
            report.entries.map { it.state })
        assertEquals(patch.digest, report.digest)
        assertArrayEquals(original, File(game, names[0]).readBytes())
        assertFalse(File(game, names[2]).exists())
    }

    @Test fun rejectsBrokenArchiveAndOverlappingCacheRoot() {
        val game = temp.newFolder()
        val cache = temp.newFolder()
        assertTrue(runCatching {
            ImagePatchImport.copyAndInspect(game, cache, byteArrayOf(1).inputStream(), { _, _ -> })
        }.isFailure)
        assertEquals(0, cache.listFiles()!!.size)
        val nested = File(game, "cache")
        assertTrue(runCatching {
            ImagePatchImport.copyAndInspect(game, nested, byteArrayOf(1).inputStream(), { _, _ -> })
        }.isFailure)
        assertFalse(nested.exists())
    }
}
