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

    @Test fun summaryPreservesOrderAndRejectsUnexpectedIds() {
        val result = ImageTextRegions.build("Start\nExit", 100, 50, sequenceOf(box to "Start", box to "Exit"))
        assertEquals("시작\n종료", ImageTextRegions.summary(result, mapOf(2 to "종료", 1 to "시작")))
        assertEquals("종료", ImageTextRegions.summary(result, mapOf(2 to "종료")))
        assertTrue(runCatching { ImageTextRegions.summary(result, mapOf(3 to "오류")) }.isFailure)
        assertTrue(runCatching { ImageTextRegions.summary(result, emptyMap()) }.isFailure)
        assertTrue(runCatching { ImageTextRegions.summary(result, mapOf(1 to "a\nb")) }.isFailure)
    }
}
