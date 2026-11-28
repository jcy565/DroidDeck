package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class ImageTextRegionsTest {
    private val box = ImageTextRegions.Box(0, 0, 20, 10)

    @Test fun invalidCoordinatesAndMissingBoxesAreExcluded() {
        val result = ImageTextRegions.build("Start\nExit", 100, 50, sequenceOf(
            box to "Start", null to "Unknown", ImageTextRegions.Box(-1, 0, 20, 10) to "Invalid",
            ImageTextRegions.Box(30, 20, 50, 30) to "Exit"))
        assertTrue(result.partial)
        assertEquals(listOf(1, 2), result.regions.map { it.id })
        assertEquals(listOf("Start", "Exit"), result.regions.map { it.text })
        assertFalse(ImageTextRegions.validBox(ImageTextRegions.Box(0, 0, 101, 10), 100, 50))
    }

    @Test fun regionsAndTextAreBounded() {
        val result = ImageTextRegions.build("text", 100, 50, (1..101).asSequence().map { box to "a" })
        assertEquals(100, result.regions.size)
        assertTrue(result.partial)
        val max = ImageTextRegions.build("text", 100, 50, (1..7).asSequence().map { box to "a".repeat(1000) })
        assertEquals(6, max.regions.size)
        assertTrue(max.partial)
        assertTrue(runCatching { ImageTextRegions.build("text", 8192, 8192, emptySequence()) }.isFailure)
    }

    @Test fun malformedUnicodeIsRejectedWithoutDiscardingValidRegions() {
        val good = "안녕 😀"
        assertEquals(good, ImageTranslationInput.validateText(good))
        for (code in 0xD800..0xDFFF) {
            val bad = "bad" + code.toChar()
            assertTrue(runCatching { ImageTranslationInput.validateText(bad) }.isFailure)
            val result = ImageTextRegions.build("Start", 100, 50,
                sequenceOf(box to bad, box to "Start"))
            assertTrue(result.partial)
            assertEquals(listOf("Start"), result.regions.map { it.text })
            assertTrue(runCatching { ImageTextRegions.summary(result, mapOf(1 to bad)) }.isFailure)
        }
    }

    @Test fun summaryUsesTheValidatedEditSnapshot() {
        val result = ImageTextRegions.build("Start\nExit", 100, 50,
            sequenceOf(box to "Start", box to "Exit"))
        val reviewed = mapOf(1 to "시작", 2 to "종료")
        val inconsistent = object : Map<Int, String> by reviewed {
            override fun get(key: Int): String? = if (key == 1) "unchecked" else reviewed[key]
        }
        assertEquals("시작\n종료", ImageTextRegions.summary(result, inconsistent))
        val unknown = object : Map<Int, String> by reviewed {
            override val entries get() = mapOf(3 to "invalid", 2 to "종료").entries
        }
        assertTrue(runCatching { ImageTextRegions.summary(result, unknown) }.isFailure)
    }

    @Test fun summaryPreservesOrderAndRejectsUnexpectedIds() {
        val result = ImageTextRegions.build("Start\nExit", 100, 50, sequenceOf(box to "Start", box to "Exit"))
        assertEquals("시작\n종료", ImageTextRegions.summary(result, mapOf(2 to "종료", 1 to "시작")))
        assertEquals("종료", ImageTextRegions.summary(result, mapOf(2 to "종료")))
        assertTrue(runCatching { ImageTextRegions.summary(result, mapOf(3 to "오류")) }.isFailure)
        assertTrue(runCatching { ImageTextRegions.summary(result, emptyMap()) }.isFailure)
        assertTrue(runCatching { ImageTextRegions.summary(result, mapOf(1 to "a\nb")) }.isFailure)
    }
}
