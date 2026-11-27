package com.droiddeck.launcher.files

import java.util.UUID

/** Manual, user-approved text exchange. No HTTP, login tokens, or AI SDK. */
internal object ChatGptExchange {
    data class Pending internal constructor(
        val target: String, val request: GameAgent.Request, val id: String, val prompt: String
    )

    fun create(target: String, request: GameAgent.Request): Pending {
        require(target.isNotBlank())
        val snapshot = request.copy(existing = request.existing.toMap())
        val id = UUID.randomUUID().toString()
        return Pending(target, snapshot, id, GameAgent.externalPrompt(snapshot, id))
    }

    fun accept(pending: Pending, currentTarget: String, currentSource: String,
        currentEdits: Map<String, String>, response: String): GameAgent.Result {
        require(pending.target == currentTarget && pending.request.source == currentSource) {
            "대상 파일 또는 원본 버전이 변경됐습니다. 새 작업을 요청하세요."
        }
        require(pending.request.existing == currentEdits) {
            "번역 초안이 변경됐습니다. 새 작업을 요청하세요."
        }
        return GameAgent.acceptExternal(pending.request, response, pending.id)
    }
}
