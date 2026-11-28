package com.droiddeck.launcher.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Local first; fallback only prepares a user-reviewed manual exchange, never transmits data. */
internal object LocalAgentFallback {
    enum class Reason(val label: String) {
        LOCAL_UNAVAILABLE("로컬 AI 실행 조건이 충족되지 않았습니다."),
        LOCAL_DECLINED("로컬 AI가 작업을 처리하기 어렵다고 판단했습니다."),
        TIMEOUT("로컬 AI 응답 시간이 초과됐습니다."),
        LOCAL_FAILED("로컬 AI 실행 또는 응답 검증에 실패했습니다.")
    }
    sealed interface Outcome {
        data class Local(val result: GameAgent.Result) : Outcome
        data class External(val pending: ChatGptExchange.Pending, val reason: Reason) : Outcome
    }

    suspend fun run(target: String, request: GameAgent.Request, localAllowed: () -> Boolean,
        externalAllowed: () -> Boolean, unchanged: suspend () -> Boolean,
        local: suspend () -> GameAgent.Result): Outcome {
        currentCoroutineContext().ensureActive()
        var result: GameAgent.Result? = null
        val reason = if (!localAllowed()) Reason.LOCAL_UNAVAILABLE else try {
            result = local()
            null
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            Reason.TIMEOUT
        } catch (e: CancellationException) {
            throw e
        } catch (_: GameAgent.NeedsExternalHelp) {
            Reason.LOCAL_DECLINED
        } catch (_: Exception) {
            Reason.LOCAL_FAILED
        }
        currentCoroutineContext().ensureActive()
        if (reason == null) {
            check(unchanged()) { "원본 또는 번역 초안이 변경됐습니다." }
            currentCoroutineContext().ensureActive()
            check(localAllowed()) { "로컬 AI 실행 조건이 변경됐습니다." }
            return Outcome.Local(requireNotNull(result))
        }
        check(externalAllowed()) { "게임 실행 또는 메모리 상태 때문에 ChatGPT 요청 준비를 보류했습니다." }
        check(unchanged()) { "원본 또는 번역 초안이 변경됐습니다. 다시 요청하세요." }
        val pending = ChatGptExchange.create(target, request)
        currentCoroutineContext().ensureActive()
        check(externalAllowed())
        return Outcome.External(pending, reason)
    }
}
