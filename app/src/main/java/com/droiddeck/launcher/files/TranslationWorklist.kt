package com.droiddeck.launcher.files

import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException

/** Reviews one source at a time. Never writes a draft, patch or source. */
internal object TranslationWorklist {
    data class Queue(val root: File, val files: List<TranslationDiscovery.Candidate>) {
        init {
            require(files.size in 1..20 && files.map { it.relative }.distinct().size == files.size)
            require(files.all { it.rows in 1..TranslationLimits.MAX_ROWS })
        }
    }
    enum class State { NOT_STARTED, IN_PROGRESS, READY_FOR_REVIEW, SOURCE_CHANGED, DRAFT_UNAVAILABLE }
    data class Entry(val candidate: TranslationDiscovery.Candidate, val state: State, val ready: Int = 0)
    data class Report(val entries: List<Entry>) {
        val totalRows get() = entries.sumOf { it.candidate.rows }
        val readyRows get() = entries.sumOf { it.ready }
        fun next(): Entry? = entries.firstOrNull { it.state == State.NOT_STARTED || it.state == State.IN_PROGRESS }
    }

    private fun <T> readable(action: () -> T): T? = try {
        action()
    } catch (e: CancellationException) { throw e }
      catch (_: IOException) { null }
      catch (_: IllegalArgumentException) { null }
      catch (_: IllegalStateException) { null }
      catch (_: SecurityException) { null }

    fun review(queue: Queue, drafts: File, checkpoint: () -> Unit = {}): Report {
        val entries = queue.files.map { candidate ->
            checkpoint()
            val snapshot = readable { TranslationDiscovery.snapshot(queue.root, candidate) }
            if (snapshot == null) Entry(candidate, State.SOURCE_CHANGED)
            else {
                checkpoint()
                val result = readable {
                    val edits = TranslationDraftStore.load(drafts, snapshot.file.canonicalPath, snapshot.source)
                    if (edits == null) Entry(candidate, State.NOT_STARTED)
                    else {
                        val ready = TranslationReuse.review(snapshot.source, edits).count(TranslationReuse.Status.READY)
                        Entry(candidate, if (ready == candidate.rows) State.READY_FOR_REVIEW else State.IN_PROGRESS, ready)
                    }
                }
                result ?: Entry(candidate, State.DRAFT_UNAVAILABLE)
            }
        }
        checkpoint()
        return Report(entries)
    }
}
