package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationDraftStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = "id\ttext\nhello\tHello {name}\nbye\tBye"

    @Test fun savedPartialDraftCanResumeWithoutApplying() {
        val dir = temporary.newFolder()
        val edits = mapOf("hello" to "안녕")
        TranslationDraftStore.save(dir, "/game/strings.tsv", source, edits)
        assertEquals(edits, TranslationDraftStore.load(dir, "/game/strings.tsv", source))
        assertTrue(runCatching { GamePatchAuthor.translation(source, edits) }.isFailure)
        val corrected = mapOf("hello" to "안녕 {name}")
        TranslationDraftStore.save(dir, "/game/strings.tsv", source, corrected)
        assertEquals(corrected, TranslationDraftStore.load(dir, "/game/strings.tsv", source))
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test fun draftsCannotBeReusedForAnotherFileOrRevision() {
        val dir = temporary.newFolder()
        TranslationDraftStore.save(dir, "/game/strings.tsv", source, mapOf("bye" to "안녕"))
        assertNull(TranslationDraftStore.load(dir, "/other/strings.tsv", source))
        assertNull(TranslationDraftStore.load(dir, "/game/strings.tsv", source + "!"))
    }

    @Test fun refusesNonregularDraftWithoutReplacingIt() {
        val dir = temporary.newFolder()
        val target = "/game/strings.tsv"
        val edits = mapOf("bye" to "안녕")
        TranslationDraftStore.save(dir, target, source, edits)
        val file = dir.listFiles()!!.single()
        assertTrue(file.delete())
        assertTrue(file.mkdir())
        assertTrue(runCatching { TranslationDraftStore.load(dir, target, source) }.isFailure)
        assertTrue(runCatching { TranslationDraftStore.save(dir, target, source, edits) }.isFailure)
        assertTrue(file.isDirectory)
        assertTrue(file.delete())
        val missing = java.io.File(dir, "missing")
        java.nio.file.Files.createSymbolicLink(file.toPath(), missing.toPath())
        assertTrue(runCatching { TranslationDraftStore.load(dir, target, source) }.isFailure)
        assertTrue(runCatching { TranslationDraftStore.save(dir, target, source, edits) }.isFailure)
        assertTrue(java.nio.file.Files.isSymbolicLink(file.toPath()))
        assertFalse(missing.exists())
        assertEquals(1, dir.listFiles()!!.size)
    }

    @Test fun invalidIdsAndCorruptedDraftsFailClosed() {
        val dir = temporary.newFolder()
        assertTrue(runCatching {
            TranslationDraftStore.save(dir, "/game/strings.tsv", source, mapOf("missing" to "안녕"))
        }.isFailure)
        assertTrue(dir.listFiles()!!.isEmpty())
        TranslationDraftStore.save(dir, "/game/strings.tsv", source, mapOf("bye" to "안녕"))
        val file = dir.listFiles()!!.single()
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertTrue(runCatching { TranslationDraftStore.load(dir, "/game/strings.tsv", source) }.isFailure)
    }
}
