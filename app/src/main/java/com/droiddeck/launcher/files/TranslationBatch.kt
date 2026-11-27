package com.droiddeck.launcher.files

import java.security.MessageDigest

/** Explicit file exchange; no network, model, or automatic changes to the game. */
internal object TranslationBatch {
    private const val SOURCE = "__dd_source_sha256"
    private const val TARGET = "__dd_target_sha256"
    private const val PREFIX = "row."

    private fun hash(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun export(target: String, source: String): String {
        require(target.isNotBlank())
        val rows = GamePatchAuthor.extract(source)
        val fields = listOf(SOURCE to hash(source), TARGET to hash(target)) +
            rows.map { PREFIX + it.id to it.text }
        return fields.joinToString(",\n", "{\n", "\n}\n") { (key, value) ->
            "  " + TranslationJson.quote(key) + ": " + TranslationJson.quote(value)
        }.also { require(it.toByteArray(Charsets.UTF_8).size <= TranslationLimits.MAX_BATCH_BYTES) }
    }

    /** Only changed values are imported. Never silently replace existing edits. */
    fun merge(target: String, source: String, payload: String,
        existing: Map<String, String>): Map<String, String> {
        require(target.isNotBlank())
        val rows = GamePatchAuthor.extract(source).associateBy { it.id }
        require(existing.keys.all { it in rows })
        require(payload.length <= TranslationLimits.MAX_BATCH_BYTES &&
            payload.toByteArray(Charsets.UTF_8).size <= TranslationLimits.MAX_BATCH_BYTES)
        val fields = TranslationJson.entries(payload, maxEntries = TranslationLimits.MAX_BATCH_ENTRIES,
            maxChars = TranslationLimits.MAX_BATCH_BYTES).associate { it.id to it.text }
        require(fields[SOURCE] == hash(source) && fields[TARGET] == hash(target)) {
            "다른 파일 또는 다른 원본 버전의 번역 작업입니다."
        }
        val incoming = linkedMapOf<String, String>()
        for ((key, text) in fields) {
            if (key == SOURCE || key == TARGET) continue
            require(key.startsWith(PREFIX)) { "알 수 없는 번역 항목입니다." }
            val id = key.removePrefix(PREFIX)
            val original = requireNotNull(rows[id]) { "알 수 없는 문장 ID입니다: $id" }
            if (text == original.text) continue
            require(existing[id] == null || existing[id] == text) { "기존 초안과 충돌합니다: $id" }
            incoming[id] = text
        }
        require(incoming.isNotEmpty()) { "번역된 문장이 없습니다." }
        GamePatchAuthor.translation(source, incoming)
        return existing + incoming
    }
}
