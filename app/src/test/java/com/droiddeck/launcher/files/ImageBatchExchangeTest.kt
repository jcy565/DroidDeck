package com.droiddeck.launcher.files

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageBatchExchangeTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun exchangeRoutesRowsAndProtectsChangedDraft() {
        val root = temp.newFolder()
        val drafts = temp.newFolder()
        val files = listOf("a.png", "b.dds").map { File(root, it).apply { writeBytes(byteArrayOf(1)) } }
        val regions = ImageTextRegions.build("Start", 32, 32,
            sequenceOf(ImageTextRegions.Box(0, 0, 20, 20) to "Start"))
        val draft = ImageDraftStore.Draft("Start", "", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, regions)
        val candidates = files.map {
            val id = ImageDraftStore.identity(it)
            ImageDraftStore.save(drafts, it, id, draft)
            ImageAssetDiscovery.Candidate(it.name, 32, 32, it.length(), id.digest)
        }
        val queue = ImageAssetDiscovery.Queue(root, candidates)
        val request = ImageBatchExchange.prepare(queue, drafts)
        assertEquals(2, request.rows.size)
        assertFalse(request.json.contains("a.png"))
        assertFalse(request.json.contains(root.canonicalPath))
        val response = request.json.replace("Start", "시작")
        val review = ImageBatchExchange.review(request, response)
        assertEquals(2, review.translatedRows)
        val manual = draft.copy(edits = mapOf(1 to "수동"), translated = "수동")
        ImageDraftStore.save(drafts, files[0], ImageDraftStore.identity(files[0]), manual)
        assertEquals(ImageBatchExchange.Applied(1, 1), ImageBatchExchange.apply(queue, drafts, review))
        assertEquals(manual, ImageDraftStore.load(drafts, ImageDraftStore.identity(files[0])))
        assertEquals(mapOf(1 to "시작"), ImageDraftStore.load(drafts, ImageDraftStore.identity(files[1]))?.edits)
        assertTrue(runCatching { ImageBatchExchange.review(request, response.replace(request.token, "wrong")) }.isFailure)
        assertTrue(runCatching { ImageBatchExchange.review(request, response.replace("i1.r1", "i9.r1")) }.isFailure)
        assertTrue(runCatching { ImageBatchExchange.review(request, request.json) }.isFailure)
    }
}
