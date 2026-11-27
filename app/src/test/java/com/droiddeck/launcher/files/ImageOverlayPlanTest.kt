package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class ImageOverlayPlanTest {
    private val first = ImageTextRegions.Box(10, 10, 30, 30)
    private fun result(second: ImageTextRegions.Box) = ImageTextRegions.build(
        "Start\nExit", 100, 100, sequenceOf(first to "Start", second to "Exit"))

    @Test fun selectedRegionsKeepOcrOrder() {
        val r = result(ImageTextRegions.Box(30, 10, 50, 30))
        assertEquals(listOf("시작", "종료"), ImageOverlayPlan.prepare(r, mapOf(2 to "종료", 1 to "시작")).map { it.text })
        assertEquals("종료", ImageOverlayPlan.prepare(r, mapOf(2 to "종료")).single().text)
    }

    @Test fun refusesOverlapEvenWithUneditedRegion() {
        val r = result(ImageTextRegions.Box(20, 20, 40, 40))
        assertTrue(runCatching { ImageOverlayPlan.prepare(r, mapOf(1 to "시작")) }.isFailure)
        assertTrue(runCatching { ImageOverlayPlan.prepare(r, mapOf(2 to "종료")) }.isFailure)
    }

    @Test fun refusesInvalidPlanAndSmallRegion() {
        val r = result(ImageTextRegions.Box(50, 50, 60, 61))
        assertTrue(runCatching { ImageOverlayPlan.prepare(r, mapOf(2 to "종료")) }.isFailure)
        assertTrue(runCatching { ImageOverlayPlan.prepare(r, emptyMap()) }.isFailure)
        assertTrue(runCatching { ImageOverlayPlan.prepare(r, mapOf(3 to "오류")) }.isFailure)
        assertTrue(runCatching { ImageOverlayPlan.prepare(r, mapOf(1 to "a\nb")) }.isFailure)
        assertTrue(runCatching { ImageOverlayPlan.prepare(r.copy(width = 1), mapOf(1 to "시작")) }.isFailure)
    }
}
