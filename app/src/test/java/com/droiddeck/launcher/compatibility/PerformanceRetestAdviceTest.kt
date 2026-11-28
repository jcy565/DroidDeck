package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class PerformanceRetestAdviceTest {
    private fun observation(id: Long, fps: Double, p95: Double, scene: String = "menu",
        width: Int = 1280, game: String = "steam:1") =
        PerformanceRetestAdvice.Observation(id,
            OptimizationComparison.Sample(game, "device", scene, width, 720, fps, p95), "driver " + id)

    @Test fun suggestsRecentComparableImprovementsOnly() {
        val baseline = observation(4, 40.0, 25.0)
        val older = observation(1, 50.0, 20.0)
        val newer = observation(9, 60.0, 20.0)
        val mixed = observation(10, 60.0, 26.0)
        val wrongScene = observation(11, 100.0, 10.0, scene = "battle")
        val wrongSize = observation(12, 100.0, 10.0, width = 1920)
        val wrongGame = observation(13, 100.0, 10.0, game = "steam:2")
        val unchanged = observation(14, 40.0, 25.0)
        val result = PerformanceRetestAdvice.candidates(listOf(baseline, older, newer, mixed,
            wrongScene, wrongSize, wrongGame, unchanged), baseline.id)
        assertEquals(listOf(9L, 1L), result.map { it.observed.id })
        assertTrue(result.all { it.comparison.verdict == OptimizationComparison.Verdict.IMPROVED })
        assertEquals(4L to 9L, result[0].chronologicalIds())
        assertEquals(1L to 4L, result[1].chronologicalIds())
    }

    @Test fun boundsRecommendationsAndRequiresSelectedBaseline() {
        val baseline = observation(1, 40.0, 25.0)
        val rows = listOf(baseline) + (2L..7L).map { observation(it, 50.0, 20.0) }
        assertEquals(listOf(7L, 6L, 5L), PerformanceRetestAdvice.candidates(rows, 1).map { it.observed.id })
        assertTrue(PerformanceRetestAdvice.candidates(rows, 100).isEmpty())
    }
}
