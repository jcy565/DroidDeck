package com.droiddeck.launcher.compatibility

internal object OptimizationComparison {
    data class Sample(val gameId: String, val deviceId: String, val scene: String,
        val outputWidth: Int, val outputHeight: Int, val fps: Double, val p95Ms: Double)
    enum class Verdict { INCOMPARABLE, IMPROVED, REGRESSED, MIXED, INCONCLUSIVE }
    data class Result(val verdict: Verdict, val fpsPercent: Double? = null, val p95Percent: Double? = null)

    fun describe(result: Result): String {
        val advice = when (result.verdict) {
            Verdict.INCOMPARABLE -> return "측정 조건이 다르거나 값이 유효하지 않습니다. 동일 조건에서 다시 측정하세요."
            Verdict.IMPROVED -> "관측 성능이 개선됐습니다. 같은 장면에서 반복 측정하세요."
            Verdict.REGRESSED -> "관측 성능이 저하됐습니다. 기존 설정과 비교하고 복구 여부를 검토하세요."
            Verdict.MIXED -> "FPS와 프레임타임 결과가 엇갈립니다. 실제 플레이 안정성을 확인하세요."
            Verdict.INCONCLUSIVE -> "변화가 판정 기준보다 작습니다. 반복 측정이 필요합니다."
        }
        val fps = result.fpsPercent ?: return advice
        val p95 = result.p95Percent ?: return advice
        return java.lang.String.format(java.util.Locale.ROOT,
            "FPS %+.1f%%, P95 프레임타임 %+.1f%%. %s 설정 변경의 인과 효과를 보장하지 않습니다.",
            fps, p95, advice)
    }

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
