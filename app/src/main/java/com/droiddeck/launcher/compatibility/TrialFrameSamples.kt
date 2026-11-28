package com.droiddeck.launcher.compatibility

import kotlin.math.ceil

/** Bounded host-testable validation for a ten-second game-frame trial. */
internal object TrialFrameSamples {
    private const val MAX_SAMPLES = 20_000
    data class Metrics(val frames: Int, val fps: Double, val p95Ms: Double)

    /** One explicit trial must remain bound to the same observed game and output. */
    data class Identity(val game: String?, val live: Boolean, val steamFocused: Boolean,
        val pid: Int, val width: Int, val height: Int)

    fun requireSame(expected: Identity, observed: Identity) {
        require(expected.game?.isNotBlank() == true && expected.live && !expected.steamFocused &&
            expected.pid > 0 && expected.width > 0 && expected.height > 0 && expected == observed) {
            "게임 또는 출력이 바뀌어 측정을 중단했습니다."
        }
    }

    fun summarize(samples: List<Float>, elapsedMs: Long): Metrics {
        require(elapsedMs in 10_000L..12_000L) { "측정 시간이 유효하지 않습니다. 다시 측정하세요." }
        require(samples.size in 2..MAX_SAMPLES) { "유효한 프레임 표본이 부족하거나 한도를 초과했습니다." }
        val sorted = samples.toFloatArray()
        require(sorted.size in 2..MAX_SAMPLES && sorted.all { it.isFinite() && it > 0f }) { "유효하지 않은 프레임 표본입니다." }
        val totalMs = sorted.sumOf { it.toDouble() }
        require(totalMs >= elapsedMs * 0.8 && totalMs <= elapsedMs * 1.2) {
            "프레임 표본이 측정 시간을 충분히 대표하지 않습니다. 다시 측정하세요."
        }
        sorted.sort()
        return Metrics(sorted.size, 1000.0 * sorted.size / totalMs,
            sorted[ceil(sorted.size * 0.95).toInt() - 1].toDouble())
    }
}
