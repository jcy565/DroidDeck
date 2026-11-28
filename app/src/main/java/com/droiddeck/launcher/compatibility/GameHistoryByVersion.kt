package com.droiddeck.launcher.compatibility

/** Separate observed versions without assigning unknown records to a known version.
 * The input is newest-first and already bounded by the history query. */
internal object GameHistoryByVersion {
    data class Group(val version: String?, val summary: GameHistorySummary.Summary)

    fun summarize(observations: List<GameRunHistory.Observation>): List<Group> {
        require(observations.map { it.gameId }.distinct().size <= 1)
        return observations.groupBy { it.gameVersion }
            .map { (version, records) ->
                Group(version, GameHistorySummary.summarize(records.map { it.outcome }))
            }
    }
}
