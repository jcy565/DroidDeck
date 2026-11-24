package com.droiddeck.launcher.compatibility

import android.content.Context
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

    suspend fun report(gameId: String, limit: Int = 20): Report = withContext(Dispatchers.IO) {
        GameRunHistory(appContext).use { history ->
            analyze(history.recent(gameId, limit))
        }
    }

    companion object {
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
