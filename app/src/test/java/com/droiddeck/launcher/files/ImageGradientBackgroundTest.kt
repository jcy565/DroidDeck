package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class ImageGradientBackgroundTest {
    private val dark = 0xff203040.toInt()
    private val light = 0xffa0b0c0.toInt()
    private val ramp = (0..4).map { ImageGradientBackground.mix(dark, light, it) }

    @Test fun detectsHorizontalAndVerticalGradients() {
        assertEquals(ImageGradientBackground.Gradient(ImageGradientBackground.Axis.HORIZONTAL, dark, light),
            ImageGradientBackground.estimate(ramp, ramp, List(5) { dark }, List(5) { light }))
        assertEquals(ImageGradientBackground.Gradient(ImageGradientBackground.Axis.VERTICAL, dark, light),
            ImageGradientBackground.estimate(List(5) { dark }, List(5) { light }, ramp, ramp))
        assertEquals(0xff607080.toInt(), ramp[2])
    }

    @Test fun rejectsIrregularTransparentOrIncompleteEdges() {
        val irregular = ramp.toMutableList().apply { this[2] = 0xffff0000.toInt() }
        assertNull(ImageGradientBackground.estimate(irregular, ramp, List(5) { dark }, List(5) { light }))
        assertNull(ImageGradientBackground.estimate(ramp, ramp, List(5) { 0 }, List(5) { light }))
        assertNull(ImageGradientBackground.estimate(ramp.dropLast(1), ramp, List(5) { dark }, List(5) { light }))
        assertTrue(runCatching { ImageGradientBackground.mix(dark, light, 5) }.isFailure)
    }
}
