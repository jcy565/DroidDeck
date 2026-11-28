package com.droiddeck.launcher.files

import com.droiddeck.ai.core.AiCore

/** Manual, user-approved text exchange. No HTTP, login tokens, or AI SDK. */
internal object ChatGptExchange {
    data class Pending internal constructor(
        val request: GameAgent.Request, val prompt: String, internal val binding: AiCore.Exchange
    ) {
        val id: String get() = binding.id()
    }

    private fun snapshotEdits(edits: Map<String, String>): Map<String, String> {
        require(edits.size <= TranslationLimits.MAX_ROWS)
        val stable = edits.toMap()
        require(stable.size <= TranslationLimits.MAX_ROWS)
        stable.forEach { (key, value) ->
            TranslationJson.validUnicode(key)
            TranslationJson.validUnicode(value)
        }
        return java.util.Collections.unmodifiableMap(stable)
    }

    fun create(target: String, request: GameAgent.Request): Pending {
        AiHostBridge.requireCompatible()
        require(target.isNotBlank())
        TranslationJson.validUnicode(target)
        TranslationJson.validUnicode(request.source)
        val snapshot = request.copy(existing = snapshotEdits(request.existing))
        val binding = AiCore.Exchange(target, snapshot.source, snapshot.existing)
        return Pending(snapshot, GameAgent.externalPrompt(snapshot, binding.id()), binding)
    }

    fun checkpoint(pending: Pending, currentTarget: String, currentSource: String,
        currentEdits: Map<String, String>): ExchangeCheckpoint.Saved {
        AiHostBridge.requireCompatible()
        val stable = snapshotEdits(currentEdits)
        require(pending.binding.check(currentTarget, currentSource, stable) == AiCore.ExchangeStatus.MATCH) {
            "원본 또는 번역 초안이 변경됐습니다. 새 작업을 요청하세요."
        }
        return ExchangeCheckpoint.Saved(pending.id, pending.request.instruction, pending.request.start,
            ExchangeCheckpoint.fingerprint(currentTarget, currentSource, stable, pending.prompt))
    }

    fun restore(saved: ExchangeCheckpoint.Saved, target: String, extension: String,
        source: String, edits: Map<String, String>): Pending {
        AiHostBridge.requireCompatible()
        val request = GameAgent.Request(saved.instruction, extension, source, snapshotEdits(edits), saved.start)
        val prompt = GameAgent.externalPrompt(request, saved.id)
        require(ExchangeCheckpoint.fingerprint(target, source, request.existing, prompt) == saved.fingerprint) {
            "다른 파일, 변경된 원본·초안 또는 AI 요청 버전입니다. 새 작업을 요청하세요."
        }
        return Pending(request, prompt, AiCore.Exchange(target, source, request.existing, saved.id))
    }

    data class TranslationResume(val pending: Pending, val rows: List<GamePatchAuthor.Row>,
        val draft: TranslationDraftStore.Draft?)

    /** Recover only against the exact saved source revision and edits. */
    fun resumeTranslation(saved: ExchangeCheckpoint.Saved, target: String, extension: String,
        source: String, draft: TranslationDraftStore.Draft?): TranslationResume {
        require(GamePatchAuthor.matchesTranslationExtension(source, extension))
        val rows = GamePatchAuthor.extract(source)
        val pending = restore(saved, target, extension, source, draft?.edits.orEmpty())
        return TranslationResume(pending, rows, draft)
    }

    fun accept(pending: Pending, currentTarget: String, currentSource: String,
        currentEdits: Map<String, String>, response: String): GameAgent.Result {
        AiHostBridge.requireCompatible()
        val stable = snapshotEdits(currentEdits)
        when (pending.binding.check(currentTarget, currentSource, stable)) {
            AiCore.ExchangeStatus.MATCH -> Unit
            AiCore.ExchangeStatus.SOURCE_CHANGED -> error("대상 파일 또는 원본 버전이 변경됐습니다. 새 작업을 요청하세요.")
            AiCore.ExchangeStatus.DRAFT_CHANGED -> error("번역 초안이 변경됐습니다. 새 작업을 요청하세요.")
            else -> error("알 수 없는 작업 상태입니다.")
        }
        return GameAgent.acceptExternal(pending.request, response, pending.id)
    }
}
