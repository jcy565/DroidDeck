package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class TranslationBatchTest {
    private val target = "/game/strings.json"
    private val source = "{\"hello\":\"Hello {name}\",\"bye\":\"Bye\"}"
    private val batch = TranslationBatch.export(target, source)
    private val translated = batch.replace("Hello {name}", "안녕 {name}").replace("\"Bye\"", "\"안녕히\"")

    @Test fun exportAndMergeOnlyChangedRows() {
        assertFalse(batch.contains(target))
        assertEquals(mapOf("hello" to "안녕 {name}", "bye" to "안녕히"),
            TranslationBatch.merge(target, source, translated, emptyMap()))
        assertTrue(runCatching { TranslationBatch.merge(target, source, batch, emptyMap()) }.isFailure)
    }

    @Test fun rejectsDifferentFileRevisionAndDraftConflict() {
        assertTrue(runCatching { TranslationBatch.merge("/other/strings.json", source, translated, emptyMap()) }.isFailure)
        assertTrue(runCatching { TranslationBatch.merge(target, source + " ", translated, emptyMap()) }.isFailure)
        assertTrue(runCatching { TranslationBatch.merge(target, source, translated,
            mapOf("hello" to "다른 번역 {name}")) }.isFailure)
    }

    @Test fun rejectsUnknownAndInvalidTranslationWithoutChangingDraft() {
        val draft = mapOf("bye" to "안녕히")
        for (invalid in listOf(translated.replace("안녕 {name}", "안녕"),
            translated.replace("row.hello", "row.unknown"), translated.replace("row.bye", "row.hello"))) {
            assertTrue(runCatching { TranslationBatch.merge(target, source, invalid, draft) }.isFailure)
        }
        assertEquals(mapOf("bye" to "안녕히"), draft)
    }

    @Test fun supportsFullHundredRowBatchWithMetadata() {
        val many = (1..100).joinToString("\n", "id\ttext\n") { "id$it\tText $it" }
        val payload = TranslationBatch.export(target, many)
        assertEquals(102, TranslationJson.entries(payload, 102).size)
        assertTrue(runCatching { TranslationJson.entries(payload) }.isFailure)
        assertEquals(mapOf("id1" to "문장 1"), TranslationBatch.merge(target, many,
            payload.replace("\"Text 1\"", "\"문장 1\""), emptyMap()))
    }
}
