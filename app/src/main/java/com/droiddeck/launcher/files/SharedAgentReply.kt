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
        if (body.startsWith("```json\n") && body.endsWith("\n```"))
            body = body.removePrefix("```json\n").removeSuffix("\n```").trim()
        require(body.length in 1..GameAgent.MAX_OUTPUT_CHARS)
        val fields = TranslationJson.entries(body, maxEntries = GameAgent.MAX_TRANSLATION_ROWS + 4,
            maxChars = GameAgent.MAX_OUTPUT_CHARS).associate { it.id to it.text }
        require(fields["exchange_id"] == expectedId) { "저장된 AI 요청과 일치하는 응답이 아닙니다." }
        require(fields["action"] in setOf("translate", "mod", "advice")) { "지원하지 않는 응답입니다." }
        require(!fields["explanation"].isNullOrBlank()) { "응답 설명이 없습니다." }
        return body
    }

    private fun prefs(context: Context) = context.getSharedPreferences("ai_reply_inbox", Context.MODE_PRIVATE)

    @Synchronized fun receive(context: Context, text: CharSequence) {
        val saved = ExchangeCheckpoint.load(File(context.noBackupFilesDir, "ai-exchange/checkpoint.bin"))
            ?: error("저장된 AI 요청이 없습니다.")
        val response = normalize(text, saved.id)
        check(prefs(context).edit().putString("id", saved.id).putString("reply", response).commit())
        changes.value++
    }

    @Synchronized fun load(context: Context, id: String): String? {
        val store = prefs(context)
        if (store.getString("id", null) != id) return null
        val saved = ExchangeCheckpoint.load(File(context.noBackupFilesDir, "ai-exchange/checkpoint.bin"))
        if (saved?.id != id) return null
        return store.getString("reply", null)?.let { normalize(it, id) }
    }

    /** Explicit discard from the editor also removes the unaccepted response. */
    @Synchronized fun discard(context: Context) {
        check(prefs(context).edit().remove("id").remove("reply").commit())
        changes.value++
    }

    @Synchronized fun clear(context: Context, id: String) {
        val store = prefs(context)
        if (store.getString("id", null) == id) {
            check(store.edit().remove("id").remove("reply").commit())
            changes.value++
        }
    }
}
