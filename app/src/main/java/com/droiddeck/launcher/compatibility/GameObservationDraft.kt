package com.droiddeck.launcher.compatibility

/** Manual observation is attributed to an installed version only by explicit choice. */
internal object GameObservationDraft {
    enum class Attribution { OBSERVED_INSTALL, UNKNOWN }

    data class Draft internal constructor(val gameId: String, val installedVersion: String?,
        val outcome: GameRunHistory.Outcome)

    fun prepare(gameId: String, installedVersion: String?, outcome: GameRunHistory.Outcome): Draft {
        GameHistoryQuery.validateIdentity(gameId, installedVersion)
        require(outcome != GameRunHistory.Outcome.STARTED)
        return Draft(gameId, installedVersion, outcome)
    }

    fun confirm(draft: Draft, currentGameId: String, currentInstalledVersion: String?,
        attribution: Attribution, recordedAt: Long): GameRunHistory.Observation {
        require(draft.gameId == currentGameId && draft.installedVersion == currentInstalledVersion) {
            "선택한 게임 또는 설치 버전이 변경되었습니다. 다시 기록해 주세요."
        }
        require(recordedAt >= 0)
        val version = when (attribution) {
            Attribution.OBSERVED_INSTALL -> requireNotNull(draft.installedVersion)
            Attribution.UNKNOWN -> null
        }
        GameHistoryQuery.validateIdentity(draft.gameId, version)
        require(draft.outcome != GameRunHistory.Outcome.STARTED)
        return GameRunHistory.Observation(draft.gameId, version, draft.outcome, recordedAt)
    }
}
