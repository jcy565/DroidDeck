package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedExchangeFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun onlyListsBoundedUniqueSupportedDirectChildren() {
        val folder = temporary.newFolder("game")
        val files = (0 until 40).map { File(folder, "file$it.INI").apply { writeText("original") } }
        val nested = File(folder, "sub").apply { mkdir() }
        val outside = temporary.newFile("outside.ini")
        val unsupported = File(folder, "image.png").apply { writeText("original") }
        val candidates = files + listOf(files.first(), File(nested, "nested.ini"), outside, unsupported)
        val page = SavedExchangeFiles.candidates(folder, candidates, "")
        assertEquals(files.take(30), page.files)
        assertTrue(page.more)
        val filtered = SavedExchangeFiles.candidates(folder, candidates, " FILE1 ")
        assertEquals(files.filter { it.name.contains("file1", ignoreCase = true) }, filtered.files)
        assertFalse(filtered.more)
        assertTrue(SavedExchangeFiles.candidates(folder, candidates, "outside").files.isEmpty())
        assertTrue(SavedExchangeFiles.candidates(folder, candidates, "nested").files.isEmpty())
    }

    @Test fun selectionRejectsMissingNonregularAndOutsideFiles() {
        val folder = temporary.newFolder("game")
        val original = File(folder, "menu.json").apply { writeText("original") }
        val outside = temporary.newFile("outside.ini").apply { writeText("outside") }
        val link = File(folder, "linked.ini")
        Files.createSymbolicLink(link.toPath(), outside.toPath())
        val dir = File(folder, "folder.ini").apply { mkdir() }
        assertEquals(original, SavedExchangeFiles.selected(folder, original))
        for (invalid in listOf(outside, link, dir, File(folder, "missing.ini"))) {
            assertTrue(runCatching { SavedExchangeFiles.selected(folder, invalid) }.isFailure)
        }
        assertEquals("original", original.readText())
        assertEquals("outside", outside.readText())
        assertTrue(Files.isSymbolicLink(link.toPath()))
    }
}
