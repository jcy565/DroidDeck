package com.droiddeck.launcher.compatibility

import com.droiddeck.launcher.hud.FrameStats
import kotlin.math.abs

/** Host-only synthetic frame pipeline; does not load JNI or Android. */
fun main() {
    var assertions = 0
    fun verify(ok: Boolean) { check(ok) { "Assertion ${assertions + 1}" }; assertions++ }
    fun rejects(action: () -> Unit) = verify(runCatching(action).isFailure)
    fun trial(fps: Int, batch: Int): TrialFrameSamples.Metrics {
        val stats = FrameStats()
        val ring = FloatArray(FrameStats.GRAPH_FRAMES)
        val interval = (1e9 / fps).toLong()
        var now = 1_000_000_000L
        stats.add(interval, now)
        var cursor = stats.graphSnapshot(ring)
        val samples = ArrayList<Float>()
        repeat(fps * 10) { i ->
            now += interval
            stats.add(interval, now)
            if ((i + 1) % batch == 0 || i + 1 == fps * 10) {
                val next = stats.graphSnapshot(ring)
                val count = next.newFramesSince(cursor)
                verify(count in 1..batch)
                for (j in ring.size - count until ring.size) samples.add(ring[j])
                cursor = next
            }
        }
        val metrics = TrialFrameSamples.summarize(samples, 10_000)
        verify(metrics.frames == fps * 10)
        verify(abs(metrics.fps - fps) < 0.001)
        verify(abs(metrics.p95Ms - 1000.0 / fps) < 0.001)
        return metrics
    }
    for (fps in 5..240) for (batch in listOf(7, 31, 200)) trial(fps, batch)
    val before = trial(40, 17)
    val after = trial(60, 17)
    val base = OptimizationComparison.Sample("steam:1", "device", "scene", 1280, 720, before.fps, before.p95Ms)
    val changed = base.copy(fps = after.fps, p95Ms = after.p95Ms)
    val comparison = OptimizationComparison.compare(base, changed)
    verify(comparison.verdict == OptimizationComparison.Verdict.IMPROVED)
    verify(abs(requireNotNull(comparison.fpsPercent) - 50.0) < 0.001)
    verify(OptimizationComparison.compare(base, changed.copy(outputWidth = 1600)).verdict == OptimizationComparison.Verdict.INCOMPARABLE)
    for (i in 1..1000) {
        val n = i / 7.0
        val sample = base.copy(fps = n, p95Ms = n)
        for ((factor, verdict) in listOf(1.05 to OptimizationComparison.Verdict.IMPROVED,
            0.95 to OptimizationComparison.Verdict.REGRESSED, 1.04999 to OptimizationComparison.Verdict.INCONCLUSIVE)) {
            verify(OptimizationComparison.compare(sample, sample.copy(fps = n * factor)).verdict == verdict)
        }
    }
    val stats = FrameStats()
    val ring = FloatArray(FrameStats.GRAPH_FRAMES)
    var now = 1_000_000_000L
    fun add(n: Int) = repeat(n) { now += 10_000_000L; stats.add(10_000_000L, now) }
    for (count in 0..200) {
        stats.reset(); add(10)
        val beforeReset = stats.graphSnapshot(ring)
        add(count)
        val next = stats.graphSnapshot(ring)
        verify(next.newFramesSince(beforeReset) == count)
        stats.reset(); add(220)
        rejects { stats.graphSnapshot(ring).newFramesSince(next) }
        val fresh = stats.graphSnapshot(ring)
        add(201)
        rejects { stats.graphSnapshot(ring).newFramesSince(fresh) }
        val beforeInvalid = stats.graphSnapshot(ring)
        stats.add(0L, now)
        rejects { stats.graphSnapshot(ring).newFramesSince(beforeInvalid) }
        val beforeDuplicate = stats.graphSnapshot(ring)
        stats.add(10_000_000L, now)
        rejects { stats.graphSnapshot(ring).newFramesSince(beforeDuplicate) }
    }
    for (bad in listOf(emptyList(), listOf(16f, 16f), listOf(8000f, 8000f), listOf(5000f, Float.NaN), listOf(5000f, 0f), List(20001) { .5f })) {
        rejects { TrialFrameSamples.summarize(bad, 10_000) }
    }
    println("PASS $assertions performance-core assertions")
}
