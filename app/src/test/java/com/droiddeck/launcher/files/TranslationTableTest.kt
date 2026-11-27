package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationTableTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = "\"id\",text\r\n\"greeting\",\"Hello, \"\"friend\"\" {name}\"\r\nbye,Bye\r\n"
    private val edits = mapOf("greeting" to "안녕, \"친구\" {name}")
    private val expected = "\"id\",text\r\n\"greeting\",\"안녕, \"\"친구\"\" {name}\"\r\nbye,Bye\r\n"

    @Test fun csvPreservesQuotingAndUntouchedLines() {
        assertEquals("Hello, \"friend\" {name}", GamePatchAuthor.extract(source).first().text)
        assertEquals(expected, GamePatchAuthor.translation(source, edits).proposed)
        assertEquals(edits.getValue("greeting"), GamePatchAuthor.extract(expected).first().text)
        assertEquals(source, GamePatchAuthor.translation(source, mapOf("bye" to "Bye")).proposed)
    }

    @Test fun invalidCsvAndMissingVariablesAreRejected() {
        for (invalid in listOf("id,text\nx,\"hello\nworld\"", "id,text\nx,a,b",
            "id,text\nx,abc\"def", "id,text\nx,\"a\"bad", "id,text\nx,a\nx,b", "id,text\nx,")) {
            assertTrue(runCatching { GamePatchAuthor.extract(invalid) }.isFailure)
        }
        assertTrue(runCatching { GamePatchAuthor.translation(source, mapOf("greeting" to "안녕")) }.isFailure)
        assertTrue(TranslationTable.matchesExtension(source, "CSV"))
        assertFalse(TranslationTable.matchesExtension(source, "tsv"))
    }

    @Test fun csvDraftAndZipRoundTrip() {
        val dir = temporary.newFolder()
        TranslationDraftStore.save(dir, "/game/strings.csv", source, edits)
        assertEquals(edits, TranslationDraftStore.load(dir, "/game/strings.csv", source))
        assertNull(TranslationDraftStore.load(dir, "/game/strings.csv", source + "\n"))
        val zip = GamePatchAuthor.zip("strings.csv", GamePatchAuthor.translation(source, edits))
        assertEquals(expected, LocalGameAdvisor.replacement("strings.csv", "patch.zip", zip.inputStream()))
    }
}
