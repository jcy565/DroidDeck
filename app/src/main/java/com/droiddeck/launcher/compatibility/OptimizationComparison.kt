package com.droiddeck.launcher.compatibility

internal object OptimizationComparison {
    data class Sample(val gameId: String, val deviceId: String, val scene: String,
        val outputWidth: Int, val outputHeight: Int, val fps: Double, val p95Ms: Double)
    enum class Verdict { INCOMPARABLE, IMPROVED, REGRESSED, MIXED, INCONCLUSIVE }
    data class Result(val verdict: Verdict, val fpsPercent: Double? = null, val p95Percent: Double? = null)

    private fun valid(s: Sample) = s.gameId.isNotBlank() && s.deviceId.isNotBlank() &&
        s.scene.isNotBlank() && s.outputWidth > 0 && s.outputHeight > 0 &&
        s.fps.isFinite() && s.fps > 0 && s.p95Ms.isFinite() && s.p95Ms > 0

    /** Scene labels are user assertions, not automatic proof of identical workloads. */
    fun compare(before: Sample, after: Sample): Result {
        if (!valid(before) || !valid(after) || before.gameId != after.gameId ||
            before.deviceId != after.deviceId || before.scene != after.scene ||
            before.outputWidth != after.outputWidth || before.outputHeight != after.outputHeight)
            return Result(Verdict.INCOMPARABLE)
        val fps = (after.fps / before.fps - 1) * 100
        val p95 = (after.p95Ms / before.p95Ms - 1) * 100
        if (!fps.isFinite() || !p95.isFinite()) return Result(Verdict.INCOMPARABLE)
        val better = fps >= 5 || p95 <= -5
        val worse = fps <= -5 || p95 >= 5
        val verdict = when {
            better && worse -> Verdict.MIXED
            better -> Verdict.IMPROVED
            worse -> Verdict.REGRESSED
            else -> Verdict.INCONCLUSIVE
        }
        return Result(verdict, fps, p95)
    }
}
