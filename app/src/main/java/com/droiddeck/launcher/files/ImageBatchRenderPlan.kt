package com.droiddeck.launcher.files

import java.io.File
import java.util.concurrent.CancellationException

/** Geometry and saved draft preflight only; rendering can still fail on pixels or fonts. */
internal object ImageBatchRenderPlan {
    enum class State { READY, MISSING, INCOMPLETE, INVALID }
    data class Entry(val candidate: ImageAssetDiscovery.Candidate, val state: State)
    fun requireReady(c: ImageAssetDiscovery.Candidate, draft: ImageDraftStore.Draft): ImageTextRegions.Result {
        val regions = requireNotNull(draft.regions)
        require(!regions.partial && draft.rotation == ImageTranslationInput.Rotation.NONE &&
            regions.regions.isNotEmpty() && draft.edits.keys == regions.regions.map { it.id }.toSet())
        require(draft.translated == ImageTextRegions.summary(regions, draft.edits))
        ImageOverlayPlan.originalSize(ImageOverlayPlan.prepare(regions, draft.edits),
            regions.width, regions.height, c.width, c.height)
        return regions
    }

    fun inspect(queue: ImageAssetDiscovery.Queue, drafts: File, checkpoint: () -> Unit = {}): List<Entry> =
        queue.candidates.map { c ->
            checkpoint()
            val state = try {
                val file = ImageAssetDiscovery.open(queue.root, c)
                val id = ImageDraftStore.Identity(file.canonicalPath, c.digest)
                val draft = ImageDraftStore.load(drafts, id)
                val regions = draft?.regions
                when {
                    draft == null -> State.MISSING
                    regions == null || regions.partial || draft.rotation != ImageTranslationInput.Rotation.NONE ||
                        regions.regions.isEmpty() || draft.edits.keys != regions.regions.map { it.id }.toSet() -> State.INCOMPLETE
                    else -> {
                        requireReady(c, draft)
                        State.READY
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { State.INVALID }
            checkpoint()
            Entry(c, state)
        }
}
