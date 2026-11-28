package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class TranslationReuseTest {
    private val source = "id\ttext\na\tHello {name}\nb\tHello {name}\nc\tHello {name}\nd\tBye"

    @Test fun fillsOnlyExactMissingDuplicatesWithoutChangingInput() {
        val original = mapOf("a" to "안녕 {name}")
        val result = TranslationReuse.fill(source, original)
        assertEquals(2, result.added)
        assertEquals(0, result.conflicts)
        assertEquals("안녕 {name}", result.edits["b"])
        assertEquals("안녕 {name}", result.edits["c"])
        assertFalse(result.edits.containsKey("d"))
        assertEquals(1, original.size)
        assertTrue(GamePatchAuthor.translation(source, result.edits).proposed.contains("b\t안녕 {name}"))
    }

    @Test fun conflictingOrInvalidTranslationsDoNotPropagate() {
        val conflict = TranslationReuse.fill(source,
            mapOf("a" to "안녕 {name}", "b" to "반가워 {name}"))
        assertEquals(0, conflict.added)
        assertEquals(1, conflict.conflicts)
        assertFalse(conflict.edits.containsKey("c"))
        for (value in listOf("안녕", "Hello {name}")) {
            val result = TranslationReuse.fill(source, mapOf("a" to value))
            assertEquals(0, result.added)
            assertEquals(2, result.conflicts)
        }
    }

    @Test fun reviewAndReuseReadOnlyTheValidatedEditSnapshot() {
        val reviewed = mapOf("a" to "안녕 {name}", "d" to "잘 가")
        val inconsistent = object : Map<String, String> by reviewed {
            override fun get(key: String): String? = if (key == "a") "invalid" else reviewed[key]
        }
        val review = TranslationReuse.review(source, inconsistent)
        assertEquals(2, review.count(TranslationReuse.Status.READY))
        assertEquals(2, review.count(TranslationReuse.Status.MISSING))
        val result = TranslationReuse.fill(source, inconsistent)
        assertEquals(2, result.added)
        assertEquals(0, result.conflicts)
        assertEquals("안녕 {name}", result.edits["b"])
        assertEquals("안녕 {name}", result.edits["c"])
        assertEquals(2, reviewed.size)
        val unknown = object : Map<String, String> by reviewed {
            override val entries get() = mapOf("unknown" to "x", "d" to "잘 가").entries
        }
        assertTrue(runCatching { TranslationReuse.review(source, unknown) }.isFailure)
        assertTrue(runCatching { TranslationReuse.fill(source, unknown) }.isFailure)
        assertTrue(runCatching {
            review.nextUnfinished(listOf("a", "a", "b", "c", "d"), 0)
        }.isFailure)
    }

    @Test fun reviewDistinguishesMissingInvalidAndUnchanged() {
        val review = TranslationReuse.review(source,
            mapOf("a" to "안녕 {name}", "b" to "안녕", "c" to "Hello {name}"))
        for (status in TranslationReuse.Status.values()) assertEquals(1, review.count(status))
        assertEquals(1, review.nextUnfinished(listOf("a", "b", "c", "d"), 0))
        assertEquals(1, review.nextUnfinished(listOf("a", "b", "c", "d"), 3))
        assertTrue(runCatching { TranslationReuse.review(source, mapOf("bad" to "안녕")) }.isFailure)
    }
}
