package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationJsonTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = "{\r\n  \"hello\" : \"Hello {name}\",\r\n  \"bye\": \"B\\u0079e\"\r\n}"
    private val edits = mapOf("hello" to "안녕, \"친구\" {name}")
    private val expected = "{\r\n  \"hello\" : \"안녕, \\\"친구\\\" {name}\",\r\n  \"bye\": \"B\\u0079e\"\r\n}"

    @Test fun onlyTranslatedValueChanges() {
        assertEquals(listOf("Hello {name}", "Bye"), GamePatchAuthor.extract(source).map { it.text })
        assertEquals(expected, GamePatchAuthor.translation(source, edits).proposed)
        assertEquals(source, GamePatchAuthor.translation(source, mapOf("bye" to "Bye")).proposed)
        assertEquals(edits.getValue("hello"), GamePatchAuthor.extract(expected).first().text)
        assertTrue(GamePatchAuthor.matchesTranslationExtension(source, "JSON"))
        assertFalse(GamePatchAuthor.matchesTranslationExtension(source, "csv"))
    }

    @Test fun rejectsUnsupportedAndAmbiguousJson() {
        val invalid = listOf("{}", "{\"x\":1}", "{\"x\":null}", "{\"x\":[]}",
            "{\"x\":{\"a\":\"A\"}}", "{\"x\":\"A\",\"\\u0078\":\"B\"}",
            "{\"x\":\"A\",}", "{\"x\":\"\\q\"}", "{\"x\":\"\\uD800\"}",
            "{\"x\":\"A\"}{}", "{\"x\":\"\\n\"}", "{\"x\":\"\\u0000\"}")
        for (input in invalid) assertTrue(input, runCatching { GamePatchAuthor.extract(input) }.isFailure)
        assertTrue(runCatching { GamePatchAuthor.translation(source, mapOf("hello" to "안녕")) }.isFailure)
    }

    @Test fun jsonDraftAndExportReuseExistingPipeline() {
        val dir = temporary.newFolder()
        TranslationDraftStore.save(dir, "/game/strings.json", source, edits)
        assertEquals(edits, TranslationDraftStore.load(dir, "/game/strings.json", source))
        assertNull(TranslationDraftStore.load(dir, "/game/strings.json", source + " "))
        val zip = GamePatchAuthor.zip("strings.json", GamePatchAuthor.translation(source, edits))
        assertEquals(expected, LocalGameAdvisor.replacement("strings.json", "patch.zip", zip.inputStream()))
    }
}
