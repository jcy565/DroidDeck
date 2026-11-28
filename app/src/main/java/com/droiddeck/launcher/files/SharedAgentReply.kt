package com.droiddeck.launcher.files

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Untrusted incoming text, only for the currently saved exchange. No source-file writes. */
internal object SharedAgentReply {
    private val changes = MutableStateFlow(0L)
    val revision = changes.asStateFlow()

    fun normalize(text: CharSequence, expectedId: String): String {
        require(text.length in 1..GameAgent.MAX_OUTPUT_CHARS + 32) { "응답 길이가 지원 범위를 벗어났습니다." }
        var body = text.toString().trim()
        val fence = if (body.startsWith("```json\r\n")) "\r\n" else "\n"
        if (body.startsWith("```json" + fence) && body.endsWith(fence + "```"))
            body = body.removePrefix("```json" + fence).removeSuffix(fence + "```").trim()
        require(body.length in 1..GameAgent.MAX_OUTPUT_CHARS)
        val fields = TranslationJson.entries(body, maxEntries = GameAgent.MAX_TRANSLATION_ROWS + 4,
            maxChars = GameAgent.MAX_OUTPUT_CHARS).associate { it.id to it.text }
        require(fields["exchange_id"] == expectedId) { "저장된 AI 요청과 일치하는 응답이 아닙니다." }
        require(fields["action"] in setOf("translate", "mod", "advice")) { "지원하지 않는 응답입니다." }
        require(!fields["explanation"].isNullOrBlank()) { "응답 설명이 없습니다." }
        return body
    }

    private fun prefs(context: Context) = context.getSharedPreferences("ai_reply_inbox", Context.MODE_PRIVATE)

    data class Replacement(val checkpoint: ExchangeCheckpoint.Saved, val previous: String, val incoming: String)

    private fun checkpointFile(context: Context) = File(context.noBackupFilesDir, "ai-exchange/checkpoint.bin")

    private fun checkpoint(context: Context) = ExchangeCheckpoint.load(checkpointFile(context))

    enum class Status(val label: String) {
        NO_REQUEST("저장된 ChatGPT 요청 없음"),
        WAITING("저장된 ChatGPT 요청 있음 · 공유 응답 대기"),
        READY("공유 응답 수신 완료 · 원래 파일에서 재개 및 검증 필요"),
    }

    /** Device-wide status only; no request instruction, file path or response is exposed. */
    @Synchronized fun status(context: Context): Status = synchronized(ExchangeCheckpoint) {
        val saved = checkpoint(context) ?: return@synchronized Status.NO_REQUEST
        if (load(context, saved.id) == null) Status.WAITING else Status.READY
    }

    // Serialize checkpoint and inbox operations in this process. Disk writes to the
    // two stores remain separate; a mismatched inbox is never exposed as current.
    @Synchronized fun saveCheckpoint(context: Context, saved: ExchangeCheckpoint.Saved,
        replaceExisting: Boolean) = synchronized(ExchangeCheckpoint) {
        val file = checkpointFile(context)
        if (replaceExisting) ExchangeCheckpoint.save(file, saved)
        else ExchangeCheckpoint.saveForHandoff(file, saved)
        clearOtherRequests(context, saved.id)
    }

    @Synchronized fun discardSavedExchange(context: Context): Boolean = synchronized(ExchangeCheckpoint) {
        val removed = ExchangeCheckpoint.discard(checkpointFile(context))
        discard(context)
        removed
    }

    @Synchronized fun completeSavedExchange(context: Context, id: String) = synchronized(ExchangeCheckpoint) {
        ExchangeCheckpoint.clear(checkpointFile(context), id)
        clear(context, id)
    }

    private fun publish(context: Context, id: String, response: String) {
        check(prefs(context).edit().putString("id", id).putString("reply", response).commit())
        changes.value++
    }

    /** A distinct reply for the same request needs explicit replacement approval. */
    @Synchronized fun receive(context: Context, text: CharSequence): Replacement? = synchronized(ExchangeCheckpoint) {
        val saved = checkpoint(context) ?: error("저장된 AI 요청이 없습니다.")
        val response = normalize(text, saved.id)
        val store = prefs(context)
        if (store.getString("id", null) == saved.id) {
            val previous = requireNotNull(store.getString("reply", null))
            if (previous == response) return@synchronized null
            return@synchronized Replacement(saved, previous, response)
        }
        publish(context, saved.id, response)
        null
    }

    /** Compare the reviewed checkpoint and inbox again; never apply the reply here. */
    @Synchronized fun replace(context: Context, reviewed: Replacement) = synchronized(ExchangeCheckpoint) {
        require(checkpoint(context) == reviewed.checkpoint) { "저장된 AI 요청이 변경되었습니다." }
        val store = prefs(context)
        require(store.getString("id", null) == reviewed.checkpoint.id &&
            store.getString("reply", null) == reviewed.previous) { "보관된 응답이 변경되었습니다." }
        publish(context, reviewed.checkpoint.id, normalize(reviewed.incoming, reviewed.checkpoint.id))
    }

    @Synchronized fun load(context: Context, id: String): String? = synchronized(ExchangeCheckpoint) {
        val store = prefs(context)
        if (store.getString("id", null) != id) return@synchronized null
        val saved = checkpoint(context)
        if (saved?.id != id) return@synchronized null
        requireNotNull(store.getString("reply", null)).let { normalize(it, id) }
    }

    /** Explicit discard from the editor also removes the unaccepted response. */
    @Synchronized fun discard(context: Context) {
        check(prefs(context).edit().remove("id").remove("reply").commit())
        changes.value++
    }

    @Synchronized fun clearOtherRequests(context: Context, retainedId: String) {
        val store = prefs(context)
        if (store.getString("id", null) != null && store.getString("id", null) != retainedId) {
            check(store.edit().remove("id").remove("reply").commit())
            changes.value++
        }
    }

    @Synchronized fun clear(context: Context, id: String) {
        val store = prefs(context)
        if (store.getString("id", null) == id) {
            check(store.edit().remove("id").remove("reply").commit())
            changes.value++
        }
    }
}
