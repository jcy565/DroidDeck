package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class TrialFrameSamplesTest {
    @Test fun computesFpsAndP95FromRepresentativeIntervals() {
        for (fps in 5..240) {
            val result = TrialFrameSamples.summarize(List(fps * 10) { 1000f / fps }, 10000)
            assertEquals(fps * 10, result.frames)
            assertEquals(fps.toDouble(), result.fps, 0.001)
            assertEquals(1000.0 / fps, result.p95Ms, 0.001)
        }
        val samples = (List(95) { 80f } + List(5) { 480f }).reversed()
        val result = TrialFrameSamples.summarize(samples, 10000)
        assertEquals(10.0, result.fps, 0.0)
        assertEquals(80.0, result.p95Ms, 0.0)
        assertEquals(480f, samples.first(), 0f)
    }

    @Test fun rejectsIncompleteOrInvalidMeasurements() {
        for (samples in listOf(emptyList(), listOf(16f, 16f), listOf(8000f, 8000f),
            listOf(5000f, Float.NaN), listOf(5000f, Float.POSITIVE_INFINITY),
            listOf(5000f, 0f), listOf(5000f, -1f), List(20001) { 0.5f })) {
            assertThrows(IllegalArgumentException::class.java) {
                TrialFrameSamples.summarize(samples, 10000)
            }
        }
        for (elapsed in listOf(9999L, 12001L)) {
            assertThrows(IllegalArgumentException::class.java) {
                TrialFrameSamples.summarize(List(100) { 100f }, elapsed)
            }
        }
    }

    @Test fun coverageBoundsAreInclusive() {
        for (interval in listOf(80f, 120f)) {
            assertEquals(100, TrialFrameSamples.summarize(List(100) { interval }, 10000).frames)
        }
        for (interval in listOf(79f, 121f)) {
            assertThrows(IllegalArgumentException::class.java) {
                TrialFrameSamples.summarize(List(100) { interval }, 10000)
            }
        }
    }
}
