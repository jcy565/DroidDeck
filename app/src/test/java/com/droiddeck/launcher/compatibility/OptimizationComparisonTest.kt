package com.droiddeck.launcher.compatibility

import org.junit.Assert.assertEquals
import org.junit.Test

class OptimizationComparisonTest {
    private val base = OptimizationComparison.Sample("steam:1", "device", "scene", 1280, 720, 40.0, 30.0)
    private fun verdict(after: OptimizationComparison.Sample) = OptimizationComparison.compare(base, after).verdict
    @Test fun improvement() = assertEquals(OptimizationComparison.Verdict.IMPROVED, verdict(base.copy(fps = 45.0, p95Ms = 25.0)))
    @Test fun regression() = assertEquals(OptimizationComparison.Verdict.REGRESSED, verdict(base.copy(fps = 35.0)))
    @Test fun mixed() = assertEquals(OptimizationComparison.Verdict.MIXED, verdict(base.copy(fps = 45.0, p95Ms = 35.0)))
    @Test fun smallChange() = assertEquals(OptimizationComparison.Verdict.INCONCLUSIVE, verdict(base.copy(fps = 41.0)))
    @Test fun differentOutput() = assertEquals(OptimizationComparison.Verdict.INCOMPARABLE, verdict(base.copy(outputWidth = 1920)))
    @Test fun inclusiveThresholdIsStableAcrossDifferentBaselines() {
        for (i in 1..1000) {
            val b = i / 7.0
            val before = base.copy(fps = b, p95Ms = b)
            fun compare(fps: Double, p95: Double) =
                OptimizationComparison.compare(before, before.copy(fps = fps, p95Ms = p95)).verdict
            val up = b * 1.05
            val down = b * 0.95
            assertEquals(OptimizationComparison.Verdict.IMPROVED, compare(up, b))
            assertEquals(OptimizationComparison.Verdict.REGRESSED, compare(down, b))
            assertEquals(OptimizationComparison.Verdict.REGRESSED, compare(b, up))
            assertEquals(OptimizationComparison.Verdict.IMPROVED, compare(b, down))
            assertEquals(OptimizationComparison.Verdict.MIXED, compare(up, up))
            assertEquals(OptimizationComparison.Verdict.MIXED, compare(down, down))
            assertEquals(OptimizationComparison.Verdict.INCONCLUSIVE, compare(b * 1.04999, b * 0.95001))
        }
    }
    @Test fun invalidSample() = assertEquals(OptimizationComparison.Verdict.INCOMPARABLE, verdict(base.copy(fps = Double.NaN)))
}
