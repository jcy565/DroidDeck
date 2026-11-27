package com.droiddeck.launcher.files

import java.io.File
import java.util.concurrent.CancellationException

/** Sequential OCR; keep every committed draft, never overwrite an existing draft. */
internal object ImageBatchOcr {
    enum class State { SAVED, EXISTING, FAILED }
    data class Entry(val relative: String, val state: State)
    data class Report(val entries: List<Entry>)

    suspend fun run(queue: ImageAssetDiscovery.Queue, directory: File,
        script: ImageTranslationInput.Script,
        recognize: suspend (File, ImageTranslationInput.Script) -> ImageTextRegions.Result,
        checkpoint: () -> Unit = {}): Report {
        val entries = mutableListOf<Entry>()
        for (candidate in queue.candidates) {
            checkpoint()
            val state = try {
                val file = ImageAssetDiscovery.open(queue.root, candidate)
                val identity = ImageDraftStore.Identity(file.canonicalPath, candidate.digest)
                if (ImageDraftStore.load(directory, identity) != null) State.EXISTING
                else {
                    val recognized = recognize(file, script)
                    checkpoint()
                    val draft = ImageDraftStore.Draft(recognized.text, "", script,
                        ImageTranslationInput.Rotation.NONE, recognized.takeIf { it.regions.isNotEmpty() })
                    if (ImageDraftStore.saveIfAbsent(directory, file, identity, draft)) State.SAVED
                    else State.EXISTING
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { State.FAILED }
            checkpoint()
            entries.add(Entry(candidate.relative, state))
        }
        return Report(entries.toList())
    }
}
