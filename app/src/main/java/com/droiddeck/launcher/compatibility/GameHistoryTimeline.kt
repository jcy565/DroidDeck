package com.droiddeck.launcher.compatibility

/** Render stored observations in insertion order (newest first), not inferred launch outcomes. */
internal object GameHistoryTimeline {
    const val MAX_ROWS = 20

    data class Row(val recordedAt: Long, val version: String?, val label: String)

    fun rows(observations: List<GameRunHistory.Observation>): List<Row> =
        observations.take(MAX_ROWS).map { observation ->
            Row(observation.recordedAt, observation.gameVersion, label(observation.outcome))
        }

    fun label(outcome: GameRunHistory.Outcome): String = when (outcome) {
        GameRunHistory.Outcome.STARTED -> "실행 시작 (플레이 여부 미확인)"
        GameRunHistory.Outcome.PLAYABLE -> "플레이 가능"
        GameRunHistory.Outcome.BLACK_SCREEN -> "검은 화면"
        GameRunHistory.Outcome.CRASH -> "게임 충돌"
        GameRunHistory.Outcome.UNKNOWN -> "판정 보류"
    }
}
