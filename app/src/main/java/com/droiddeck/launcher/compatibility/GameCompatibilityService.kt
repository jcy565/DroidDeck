package com.droiddeck.launcher.compatibility

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
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

    suspend fun recordInstalledOutcome(gameId: String, installFolder: File?, outcome: GameRunHistory.Outcome,
        recordedAt: Long) = withContext(Dispatchers.IO) {
        val version = InstalledGameVersion.read(gameId, installFolder)
        record(GameRunHistory.Observation(gameId, version, outcome, recordedAt))
    }

    suspend fun report(gameId: String, limit: Int = 20): Report = withContext(Dispatchers.IO) {
        check(GameplayBudget.allowsOptionalAnalysis(appContext)) { "게임 또는 메모리 상태 때문에 이력 분석을 보류합니다." }
        GameRunHistory(appContext).use { history ->
            val recent = history.recent(gameId, limit)
            check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
            analyze(recent)
        }
    }

    data class LogReport(val history: Report, val logDiagnosis: String, val limitation: String)

    /** Caller must explicitly associate the supplied log with the selected game. */
    suspend fun reportWithLog(gameId: String, log: String, limit: Int = 20): LogReport =
        withContext(Dispatchers.IO) {
            combine(report(gameId, limit), log)
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
            require(observations.map { it.gameId }.distinct().size <= 1)
            val snapshot = observations.toList()
            val summary = GameHistorySummary.summarize(snapshot.map { it.outcome })
            val latest = snapshot.firstOrNull()?.outcome ?: GameRunHistory.Outcome.UNKNOWN
            return Report(snapshot, summary, GameHistorySummary.describe(summary),
                GameHistoryAdvice.recommend(latest))
        }
    }
}
