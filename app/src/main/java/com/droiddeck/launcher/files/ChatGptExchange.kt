package com.droiddeck.launcher.files

import com.droiddeck.ai.core.AiCore

/** Manual, user-approved text exchange. No HTTP, login tokens, or AI SDK. */
internal object ChatGptExchange {
    data class Pending internal constructor(
        val request: GameAgent.Request, val prompt: String, internal val binding: AiCore.Exchange
    ) {
        val id: String get() = binding.id()
    }

    fun create(target: String, request: GameAgent.Request): Pending {
        AiHostBridge.requireCompatible()
        require(target.isNotBlank())
        val snapshot = request.copy(existing = request.existing.toMap())
        val binding = AiCore.Exchange(target, snapshot.source, snapshot.existing)
        return Pending(snapshot, GameAgent.externalPrompt(snapshot, binding.id()), binding)
    }

    fun accept(pending: Pending, currentTarget: String, currentSource: String,
        currentEdits: Map<String, String>, response: String): GameAgent.Result {
        AiHostBridge.requireCompatible()
        when (pending.binding.check(currentTarget, currentSource, currentEdits)) {
            AiCore.ExchangeStatus.MATCH -> Unit
            AiCore.ExchangeStatus.SOURCE_CHANGED -> error("대상 파일 또는 원본 버전이 변경됐습니다. 새 작업을 요청하세요.")
            AiCore.ExchangeStatus.DRAFT_CHANGED -> error("번역 초안이 변경됐습니다. 새 작업을 요청하세요.")
            else -> error("알 수 없는 작업 상태입니다.")
        }
        return GameAgent.acceptExternal(pending.request, response, pending.id)
    }
}
