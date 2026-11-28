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

    @Test fun validatesTheSameEditEntriesThatWillBeSerialized() {
        val dir = temporary.newFolder()
        val target = "/game/strings.tsv"
        val edits = mapOf("hello" to "안녕", "bye" to "잘 가")
        TranslationDraftStore.save(dir, target, source, edits)
        val record = dir.listFiles()!!.single()
        val previous = record.readBytes()
        // Model an inconsistent caller view: valid keys/values, but different entries.
        val changed = object : Map<String, String> by edits {
            override val entries get() = mapOf("unknown" to "invalid", "bye" to "안녕").entries
        }
        assertTrue(runCatching { TranslationDraftStore.save(dir, target, source, changed) }.isFailure)
        assertArrayEquals(previous, record.readBytes())
        assertEquals(edits, TranslationDraftStore.load(dir, target, source))
    }

    @Test fun invalidStorageIsNotReportedAsAnAbsentDraft() {
        val target = "/game/strings.tsv"
        val edits = mapOf("bye" to "안녕")
        val invalid = temporary.newFile().apply { writeText("preserve") }
        assertTrue(runCatching { TranslationDraftStore.load(invalid, target, source) }.isFailure)
        assertTrue(runCatching { TranslationDraftStore.save(invalid, target, source, edits) }.isFailure)
        assertEquals("preserve", invalid.readText())
        val missing = java.io.File(temporary.root, "new-drafts")
        assertNull(TranslationDraftStore.load(missing, target, source))
        TranslationDraftStore.save(missing, target, source, edits)
        assertEquals(edits, TranslationDraftStore.load(missing, target, source))
    }

    @Test fun malformedUnicodeCannotReplaceExistingDraftOrAliasTarget() {
        val dir = temporary.newFolder()
        val target = "/game/strings.tsv"
        val edits = mapOf("bye" to "안녕")
        TranslationDraftStore.save(dir, target, source, edits)
        val previous = dir.listFiles()!!.single().readBytes()
        val bad = 0xd800.toChar().toString()
        assertTrue(runCatching { TranslationDraftStore.save(dir, target, source, mapOf("bye" to bad)) }.isFailure)
        assertTrue(runCatching { TranslationDraftStore.load(dir, target + bad, source) }.isFailure)
        assertArrayEquals(previous, dir.listFiles()!!.single().readBytes())
        assertEquals(edits, TranslationDraftStore.load(dir, target, source))
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
