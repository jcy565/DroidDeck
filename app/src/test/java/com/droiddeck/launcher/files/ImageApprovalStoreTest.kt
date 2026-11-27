package com.droiddeck.launcher.files

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageApprovalStoreTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun approvesExactStagedBytesAndInvalidatesChangedDraft() {
        val root = temp.newFolder()
        val drafts = temp.newFolder()
        val stage = temp.newFolder()
        val approvals = temp.newFolder()
        val file = File(root, "menu.png").apply { writeBytes(byteArrayOf(1)) }
        val id = ImageDraftStore.identity(file)
        val regions = ImageTextRegions.build("Start", 32, 32,
            sequenceOf(ImageTextRegions.Box(0, 0, 20, 20) to "Start"))
        val draft = ImageDraftStore.Draft("Start", "시작", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, regions, mapOf(1 to "시작"))
        ImageDraftStore.save(drafts, file, id, draft)
        val candidate = ImageAssetDiscovery.Candidate(file.name, 32, 32, file.length(), id.digest)
        val queue = ImageAssetDiscovery.Queue(root, listOf(candidate))
        val verify: (ByteArray, String, ImageAssetDiscovery.Candidate) -> Unit = { bytes, ext, _ ->
            require(bytes.contentEquals(byteArrayOf(2, 3)) && ext == "png")
        }
        val report = ImageBatchRender.run(queue, drafts, stage, encode = { _, _, _, _, out, _ ->
            out.write(byteArrayOf(2, 3))
        }, verify = verify)
        val artifact = requireNotNull(report.entries.single().artifact)
        assertNull(ImageApprovalStore.read(approvals, queue, drafts, candidate, verify))
        ImageApprovalStore.approve(approvals, queue, drafts, artifact, verify)
        ImageBatchRender.discard(report)
        assertArrayEquals(byteArrayOf(2, 3), ImageApprovalStore.read(approvals, queue, drafts, candidate, verify)?.bytes)
        ImageDraftStore.save(drafts, file, id, draft.copy(translated = "출발", edits = mapOf(1 to "출발")))
        assertTrue(runCatching { ImageApprovalStore.read(approvals, queue, drafts, candidate, verify) }.isFailure)
        ImageApprovalStore.revoke(approvals, queue, candidate)
        assertNull(ImageApprovalStore.read(approvals, queue, drafts, candidate, verify))
        assertArrayEquals(byteArrayOf(1), file.readBytes())
    }
}
