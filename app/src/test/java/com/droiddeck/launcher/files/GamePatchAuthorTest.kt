package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class GamePatchAuthorTest {
    @Test fun bomTranslationFilesRoundTripThroughDiscoveryPreviewAndZip() {
        val inputs = mapOf("csv" to "\"id\",text\r\na,\"Hello {name}\"\r\nb,Bye",
            "tsv" to "id\ttext\na\tHello {name}\nb\tBye",
            "json" to """{ "a" : "Hello {name}", "b": "Bye" }""")
        for ((extension, body) in inputs) {
            val source = "\uFEFF\r\n" + body
            val name = "menu.$extension"
            assertTrue(GamePatchAuthor.matchesTranslationExtension(source, extension))
            assertEquals(listOf("a", "b"), GamePatchAuthor.extract(source).map { it.id })
            val edits = mapOf("a" to "안녕 {name}")
            val plan = GamePatchAuthor.translation(source, edits)
            val expected = source.replace("Hello {name}", "안녕 {name}")
            assertEquals(source, plan.original)
            assertEquals(expected, plan.proposed)
            assertEquals(expected, LocalGameAdvisor.replacement(name, "patch.zip",
                GamePatchAuthor.zip(name, plan).inputStream()))
            assertThrows(IllegalArgumentException::class.java) {
                GamePatchAuthor.translation(source, mapOf("a" to "안녕"))
            }
        }
    }

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

    @Test fun malformedUnicodeIsRejectedAcrossAllTranslationFormats() {
        val sources = listOf("id,text\na,Hello {name}\nb,Bye", "id\ttext\na\tHello {name}\nb\tBye",
            """{"a":"Hello {name}","b":"Bye"}""")
        for (source in sources) {
            val valid = "안녕 😀 {name}"
            val preview = GamePatchAuthor.translation(source, mapOf("a" to valid))
            assertEquals(valid, GamePatchAuthor.extract(preview.proposed).first().text)
            for (c in listOf('\uD800', '\uDBFF', '\uDC00', '\uDFFF')) {
                val invalid = "안녕 $c {name}"
                assertFalse(GamePatchAuthor.validTranslation("Hello {name}", invalid))
                assertTrue(runCatching {
                    GamePatchAuthor.translation(source, mapOf("a" to invalid))
                }.isFailure)
                assertTrue(runCatching {
                    GamePatchAuthor.extract(source.replace("Hello {name}", invalid))
                }.isFailure)
            }
        }
    }

    @Test fun authoringUsesTheSameEditSnapshotAsValidation() {
        val sources = listOf("id,text\na,Hello {name}\nb,Bye", "id\ttext\na\tHello {name}\nb\tBye",
            """{"a":"Hello {name}","b":"Bye"}""")
        val reviewed = mapOf("a" to "안녕 {name}", "b" to "잘 가")
        val inconsistent = object : Map<String, String> by reviewed {
            override fun get(key: String): String? = if (key == "a") "missing token" else reviewed[key]
        }
        for (source in sources) {
            val preview = GamePatchAuthor.translation(source, inconsistent)
            assertEquals(reviewed, GamePatchAuthor.extract(preview.proposed).associate { it.id to it.text })
        }
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
