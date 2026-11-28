package com.droiddeck.launcher.compatibility

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Explicit observations only. Callers must not infer playability from process startup. */
internal class GameCompatibilityService(context: Context) {
    private val appContext = context.applicationContext

    data class Report(
        val observations: List<GameRunHistory.Observation>,
        val summary: GameHistorySummary.Summary,
        val description: String,
        val advice: List<String>,
    )

    suspend fun record(observation: GameRunHistory.Observation) = withContext(Dispatchers.IO) {
        GameRunHistory(appContext).use { it.record(observation) }
    }

    suspend fun report(gameId: String, limit: Int = 20, gameVersion: String? = null): Report = withContext(Dispatchers.IO) {
        val checkpoint = {
            ensureActive()
            ensureAnalysisAvailable()
        }
        checkpoint()
        GameRunHistory(appContext).use { history ->
            val recent = history.recent(gameId, limit, gameVersion, checkpoint)
            checkpoint()
            val result = analyze(recent)
            checkpoint()
            result
        }
    }

    private fun ensureAnalysisAvailable() {
        if (!GameplayBudget.allowsOptionalAnalysis(appContext)) throw HistoryReadDeferred()
    }

    data class LogReport(val history: Report, val logDiagnosis: String, val limitation: String)

    /** Caller must explicitly associate the supplied log with the selected game. */
    suspend fun reportWithLog(gameId: String, log: String, limit: Int = 20): LogReport =
        withContext(Dispatchers.IO) {
            ensureActive()
            ensureAnalysisAvailable()
            val history = report(gameId, limit)
            ensureActive()
            ensureAnalysisAvailable()
            val result = combine(history, log)
            ensureActive()
            ensureAnalysisAvailable()
            result
        }

    companion object {
        internal fun combine(history: Report, log: String): LogReport {
            require(log.length <= 1_048_576) { "로그 분석 범위를 초과했습니다." }
            return LogReport(history,
                com.droiddeck.launcher.files.LocalGameAdvisor.diagnose(log),
                "로그 신호와 실행 기록은 별개 증거입니다. 동일 실행의 오류인지 확인해야 하며 원인을 확정하지 않습니다.")
        }

        /** Input is newest first, as returned by GameRunHistory.recent. */
        internal fun analyze(observations: List<GameRunHistory.Observation>): Report {
            require(observations.size <= 100)
            val snapshot = observations.toList()
            require(snapshot.size <= 100 && snapshot.map { it.gameId }.distinct().size <= 1)
            snapshot.forEach {
                GameHistoryQuery.validateIdentity(it.gameId, it.gameVersion)
                require(it.recordedAt >= 0)
            }
            val summary = GameHistorySummary.summarize(snapshot.map { it.outcome })
            val latest = snapshot.firstOrNull()?.outcome ?: GameRunHistory.Outcome.UNKNOWN
            return Report(snapshot, summary, GameHistorySummary.describe(summary),
                GameHistoryAdvice.recommend(latest))
        }
    }
}
