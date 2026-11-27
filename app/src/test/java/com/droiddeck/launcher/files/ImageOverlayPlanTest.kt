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

    @Test fun scalesSharedEdgesToOriginalDimensions() {
        val r = result(ImageTextRegions.Box(30, 10, 50, 30))
        val plan = ImageOverlayPlan.prepare(r, mapOf(1 to "시작", 2 to "종료"))
        val mapped = ImageOverlayPlan.originalSize(plan, 100, 100, 201, 199)
        assertEquals(ImageTextRegions.Box(20, 19, 60, 59), mapped.first().box)
        assertEquals(mapped.first().box.right, mapped.last().box.left)
        assertEquals(plan, ImageOverlayPlan.originalSize(plan, 100, 100, 100, 100))
        assertTrue(runCatching { ImageOverlayPlan.originalSize(plan, 100, 100, 4096, 4096) }.isFailure)
        assertTrue(runCatching { ImageOverlayPlan.originalSize(plan, 100, 100, 99, 100) }.isFailure)
    }

    @Test fun flatPaletteRejectsTransparencyAndComplexEdges() {
        val light = 0xffeeeeee.toInt()
        val dark = 0xff102030.toInt()
        assertEquals(ImageOverlayPlan.Palette(light, 0xff000000.toInt()),
            ImageOverlayPlan.flatPalette(List(20) { light }))
        assertEquals(ImageOverlayPlan.Palette(dark, 0xffffffff.toInt()),
            ImageOverlayPlan.flatPalette(List(20) { dark }))
        assertNull(ImageOverlayPlan.flatPalette(List(19) { light } + dark))
        assertNull(ImageOverlayPlan.flatPalette(List(19) { light } + 0x00101010))
        assertNull(ImageOverlayPlan.flatPalette(List(7) { light }))
        assertNotNull(ImageOverlayPlan.flatPalette(List(10) { 0xffe0e0e0.toInt() } + List(10) { light }))
    }

    @Test fun foregroundOverridesAreExplicit() {
        val palette = ImageOverlayPlan.Palette(0xffeeeeee.toInt(), 0xff000000.toInt())
        assertEquals(palette.foreground, ImageOverlayPlan.foreground(palette, ImageOverlayPlan.Foreground.AUTO))
        assertEquals(0xff000000.toInt(), ImageOverlayPlan.foreground(palette, ImageOverlayPlan.Foreground.BLACK))
        assertEquals(0xffffffff.toInt(), ImageOverlayPlan.foreground(palette, ImageOverlayPlan.Foreground.WHITE))
    }

    @Test fun comparisonPreviewUsesBoundedPowerOfTwoSample() {
        assertEquals(1, ImageOverlayPlan.comparisonSample(1024, 1024))
        assertEquals(2, ImageOverlayPlan.comparisonSample(2048, 2048))
        assertEquals(4, ImageOverlayPlan.comparisonSample(2049, 2049))
        assertTrue(runCatching { ImageOverlayPlan.comparisonSample(0, 1024) }.isFailure)
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
