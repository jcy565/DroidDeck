package com.droiddeck.launcher.compatibility

/** Summarizes recorded observations without claiming a game is universally compatible. */
internal object GameHistorySummary {
    data class Summary(val total: Int, val playable: Int, val blackScreen: Int,
        val crashes: Int, val inconclusive: Int)

    fun summarize(outcomes: List<GameRunHistory.Outcome>): Summary {
        val playable = outcomes.count { it == GameRunHistory.Outcome.PLAYABLE }
        val blackScreen = outcomes.count { it == GameRunHistory.Outcome.BLACK_SCREEN }
        val crashes = outcomes.count { it == GameRunHistory.Outcome.CRASH }
        return Summary(outcomes.size, playable, blackScreen, crashes,
            outcomes.size - playable - blackScreen - crashes)
    }

    fun describe(summary: Summary): String {
        if (summary.total == 0) return "저장된 실행 관측 기록이 없습니다."
        return "관측 기록 ${summary.total}건: 플레이 가능 ${summary.playable}건, " +
            "검은 화면 ${summary.blackScreen}건, 충돌 ${summary.crashes}건, " +
            "판정 보류 ${summary.inconclusive}건. 현재 설정의 호환성을 보장하지 않습니다."
    }
}
