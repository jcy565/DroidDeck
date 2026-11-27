package com.droiddeck.launcher.files

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull

/** Model adapters must keep game text on device and release their resources in close(). */
internal interface FileTranslationProvider : AutoCloseable {
    suspend fun translate(text: String): String
    override fun close()
}

/** A bounded, explicit draft operation, not a background service or a file writer. */
internal object TranslationEngine {
    const val MAX_WINDOW = 25
    enum class Stop { WINDOW_COMPLETE, FILE_COMPLETE, BUDGET_DENIED, PROVIDER_FAILURE, PROVIDER_TIMEOUT }
    data class Result(val edits: Map<String, String>, val nextIndex: Int, val added: Int,
        val rejected: Int, val skipped: Int, val stop: Stop)

    suspend fun run(source: String, existing: Map<String, String>, startIndex: Int = 0,
        window: Int = MAX_WINDOW, timeoutMillis: Long = 15_000,
        allowed: () -> Boolean, providerFactory: () -> FileTranslationProvider,
        progress: (Int, Int) -> Unit = { _, _ -> }): Result {
        require(window in 1..MAX_WINDOW && timeoutMillis in 1..60_000)
        val rows = GamePatchAuthor.extract(source)
        require(startIndex in 0..rows.size)
        val ids = rows.map { it.id }.toSet()
        require(existing.keys.all { it in ids })
        require(existing.values.all { it.isNotEmpty() && it.length <= TranslationLimits.MAX_TEXT_CHARS })
        val edits = existing.toMutableMap()
        val end = minOf(startIndex + window, rows.size)
        var index = startIndex
        var added = 0
        var rejected = 0
        var skipped = 0
        var provider: FileTranslationProvider? = null
        fun result(stop: Stop) = Result(edits.toMap(), index, added, rejected, skipped, stop)
        try {
            while (index < end) {
                currentCoroutineContext().ensureActive()
                if (!allowed()) return result(Stop.BUDGET_DENIED)
                val row = rows[index]
                if (row.id in edits) {
                    skipped++
                } else {
                    val output = try {
                        val active = provider ?: providerFactory().also { provider = it }
                        withTimeoutOrNull(timeoutMillis) { active.translate(row.text) }
                            ?: return result(Stop.PROVIDER_TIMEOUT)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        return result(Stop.PROVIDER_FAILURE)
                    }
                    currentCoroutineContext().ensureActive()
                    if (!allowed()) return result(Stop.BUDGET_DENIED)
                    if (output != row.text && GamePatchAuthor.validTranslation(row.text, output)) {
                        edits[row.id] = output
                        added++
                    } else rejected++
                }
                index++
                progress(index - startIndex, end - startIndex)
            }
            return result(if (index == rows.size) Stop.FILE_COMPLETE else Stop.WINDOW_COMPLETE)
        } finally {
            provider?.close()
        }
    }
}
