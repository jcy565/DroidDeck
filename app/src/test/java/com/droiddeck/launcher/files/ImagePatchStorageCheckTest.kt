package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.SecureDirectoryStream
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImagePatchStorageCheckTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun readOnlyCheckDoesNotCreateOrModifyFiles() {
        val root = temp.newFolder().canonicalFile
        val original = File(root, "menu.png").apply { writeText("original") }
        val secure = Files.newDirectoryStream(root.toPath().root).use { it is SecureDirectoryStream<*> }
        val result = ImagePatchStorageCheck.inspect(root)
        if (!secure) assertEquals(ImagePatchStorageCheck.Result.NO_SECURE_STREAM, result)
        else assertTrue(result == ImagePatchStorageCheck.Result.READ_CAPABLE ||
            result == ImagePatchStorageCheck.Result.NO_POSIX_IDENTITY)
        assertEquals(listOf("menu.png"), root.list()!!.toList())
        assertEquals("original", original.readText())
    }

    @Test fun rejectsMissingRegularFileAndSymlinkRoot() {
        val root = temp.newFolder().canonicalFile
        val file = File(root, "regular.png").apply { writeText("original") }
        assertEquals(ImagePatchStorageCheck.Result.UNSAFE_ROOT, ImagePatchStorageCheck.inspect(file))
        assertEquals(ImagePatchStorageCheck.Result.UNSAFE_ROOT, ImagePatchStorageCheck.inspect(File(root, "missing")))
        val link = File(root, "alias")
        Files.createSymbolicLink(link.toPath(), root.toPath())
        assertEquals(ImagePatchStorageCheck.Result.UNSAFE_ROOT, ImagePatchStorageCheck.inspect(link))
    }

    @Test fun propagatesCancellation() {
        val root = temp.newFolder().canonicalFile
        assertTrue(runCatching { ImagePatchStorageCheck.inspect(root) { throw CancellationException() } }
            .exceptionOrNull() is CancellationException)
    }
}
