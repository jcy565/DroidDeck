package com.droiddeck.launcher.compatibility

/** A temporary budget denial is not a database failure. */
internal class HistoryReadDeferred : IllegalStateException("History analysis deferred by gameplay or memory budget")

internal object HistoryReadFeedback {
    fun failure(saved: Boolean, deferred: Boolean): String {
        val reason = if (deferred)
            "게임 실행 상태 또는 메모리 상태 때문에 분석을 보류했습니다. 게임 종료 후 다시 조회해 주세요."
        else "실행 기록을 읽지 못했습니다. 다시 조회해 주세요."
        return if (saved) "기록은 저장되었습니다. " + reason else reason
    }
}
