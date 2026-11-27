package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationCapacityTest {
    @get:Rule val temporary = TemporaryFolder()
    private val target = "/game/strings.json"
    private val count = TranslationLimits.MAX_ROWS

    private fun sources(n: Int) = listOf(
        (1..n).joinToString("\n", "id\ttext\n") { "id$it\tText $it" },
        (1..n).joinToString("\r\n", "id,text\r\n") { "id$it,Text $it" },
        (1..n).joinToString(",\n", "{\n", "\n}") { "\"id$it\": \"Text $it\"" })

    @Test fun allFormatsSupportCapacityAndRejectOverflow() {
        val edits = (1..count).associate { "id$it" to "번역 $it" }
        for (source in sources(count)) {
            assertEquals(count, GamePatchAuthor.extract(source).size)
            val result = GamePatchAuthor.translation(source, edits).proposed
            assertEquals(edits, GamePatchAuthor.extract(result).associate { it.id to it.text })
            val dir = temporary.newFolder()
            TranslationDraftStore.save(dir, target, source, edits)
            assertEquals(edits, TranslationDraftStore.load(dir, target, source))
        }
        for (source in sources(count + 1)) {
            assertTrue(runCatching { GamePatchAuthor.extract(source) }.isFailure)
        }
    }

    @Test fun largeDraftCanResumeWithoutBypassingPatchLimit() {
        val source = sources(count).first()
        val edits = (1..count).associate { "id$it" to "한".repeat(1000) }
        val dir = temporary.newFolder()
        TranslationDraftStore.save(dir, target, source, edits)
        assertTrue(dir.listFiles()!!.single().length() > 400 * 1024)
        assertEquals(edits, TranslationDraftStore.load(dir, target, source))
        assertTrue(runCatching { GamePatchAuthor.translation(source, edits) }.isFailure)
    }

    @Test fun escapedWorklistCanExceedSourceCharLimitButNotByteLimit() {
        val source = (1..count).joinToString("\n", "id\ttext\n") {
            "id$it\t" + "\"".repeat(500)
        }
        val batch = TranslationBatch.export(target, source)
        assertTrue(batch.length > TranslationLimits.MAX_SOURCE_CHARS)
        assertEquals(mapOf("id1" to "번역"), TranslationBatch.merge(target, source,
            batch.replaceFirst("\\\"".repeat(500), "번역"), emptyMap()))
        assertTrue(runCatching {
            TranslationBatch.merge(target, source, "한".repeat(400000), emptyMap())
        }.isFailure)
    }
}
