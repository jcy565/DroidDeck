package com.droiddeck.launcher.files

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageExchangeStoreTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun restoresExactRequestAndRejectsChangedDraft() {
        val root = temp.newFolder()
        val drafts = temp.newFolder()
        val store = temp.newFolder()
        val file = File(root, "a.png").apply { writeBytes(byteArrayOf(1)) }
        val id = ImageDraftStore.identity(file)
        val regions = ImageTextRegions.build("Start", 32, 32,
            sequenceOf(ImageTextRegions.Box(0, 0, 20, 20) to "Start"))
        val draft = ImageDraftStore.Draft("Start", "", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, regions)
        ImageDraftStore.save(drafts, file, id, draft)
        val queue = ImageAssetDiscovery.Queue(root,
            listOf(ImageAssetDiscovery.Candidate(file.name, 32, 32, file.length(), id.digest)))
        assertNull(ImageExchangeStore.restore(store, queue, drafts))
        val request = ImageBatchExchange.prepare(queue, drafts)
        ImageExchangeStore.save(store, queue, request)
        assertEquals(request, ImageExchangeStore.restore(store, queue, drafts))
        assertEquals(ImageBatchRenderPlan.State.INCOMPLETE, ImageBatchRenderPlan.inspect(queue, drafts).single().state)
        val translated = draft.copy(translated = "시작", edits = mapOf(1 to "시작"))
        ImageDraftStore.save(drafts, file, id, translated)
        assertTrue(runCatching { ImageExchangeStore.restore(store, queue, drafts) }.isFailure)
        assertEquals(ImageBatchRenderPlan.State.READY, ImageBatchRenderPlan.inspect(queue, drafts).single().state)
        file.writeBytes(byteArrayOf(2))
        assertEquals(ImageBatchRenderPlan.State.INVALID, ImageBatchRenderPlan.inspect(queue, drafts).single().state)
    }
}
