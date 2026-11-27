package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class GamePatchAuthorTest {
    @Test fun numericModChangesExistingKeyOnly() {
        val source = "[Game]\r\nSpeed=1.0\r\nOther=7\r\n"
        assertEquals("[Game]\r\nSpeed=1.5\r\nOther=7\r\n",
            GamePatchAuthor.mod(source, "Game", "모드: Speed=1.5").proposed)
    }

    @Test fun rejectsMissingKeyAndTypeChanges() {
        val source = "[Game]\nSpeed=1\nEnabled=true"
        for (cmd in listOf("모드: Speed=true", "모드: Enabled=1", "모드: Missing=2",
            "모드: Speed=1;Other=2")) {
            assertTrue(runCatching { GamePatchAuthor.mod(source, "Game", cmd) }.isFailure)
        }
    }

    @Test fun translationPreservesIdsTokensUntouchedRowsAndLineEndings() {
        val source = "id\ttext\r\nhello\tHello {name} %s\\n\r\nbye\tBye\r\n"
        val expected = "id\ttext\r\nhello\t안녕 {name} %s\\n\r\nbye\tBye\r\n"
        assertEquals(expected, GamePatchAuthor.translation(source,
            mapOf("hello" to "안녕 {name} %s\\n")).proposed)
        assertEquals(listOf("hello", "bye"), GamePatchAuthor.extract(source).map { it.id })
    }

    @Test fun rejectsMalformedOrUnsafeTranslation() {
        val source = "id\ttext\nhello\tHello {name} %s"
        for (value in listOf("안녕", "안녕 %s {name}", "안녕 {name} %d",
            "안녕 {name} %s\nbad", "안녕 {name} %s <tag>")) {
            assertTrue(runCatching { GamePatchAuthor.translation(source,
                mapOf("hello" to value)) }.isFailure)
        }
        assertTrue(runCatching { GamePatchAuthor.extract("id\ttext\nx\tA\nx\tB") }.isFailure)
        assertTrue(runCatching { GamePatchAuthor.translation(source, mapOf("missing" to "안녕")) }.isFailure)
    }

    @Test fun exportedZipRoundTripsThroughExistingImporter() {
        val source = "id\ttext\nhello\tHello"
        val preview = GamePatchAuthor.translation(source, mapOf("hello" to "안녕"))
        val zip = GamePatchAuthor.zip("strings.tsv", preview)
        assertEquals(preview.proposed, LocalGameAdvisor.replacement("strings.tsv", "patch.zip",
            zip.inputStream()))
        assertTrue(runCatching { GamePatchAuthor.zip("../strings.tsv", preview) }.isFailure)
    }
}
