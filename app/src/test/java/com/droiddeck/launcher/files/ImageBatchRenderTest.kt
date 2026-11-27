package com.droiddeck.launcher.files

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageBatchRenderTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun stagesOnlyReadyDraftsAndRejectsStaleOrChangedOutput() {
        val root = temp.newFolder()
        val drafts = temp.newFolder()
        val stage = temp.newFolder()
        val files = listOf("a.png", "b.dds", "c.tga").map { File(root, it).apply { writeBytes(byteArrayOf(1)) } }
        val regions = ImageTextRegions.build("Start", 32, 32,
            sequenceOf(ImageTextRegions.Box(0, 0, 20, 20) to "Start"))
        val draft = ImageDraftStore.Draft("Start", "시작", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, regions, mapOf(1 to "시작"))
        val candidates = files.mapIndexed { index, file ->
            val id = ImageDraftStore.identity(file)
            if (index != 2) ImageDraftStore.save(drafts, file, id, draft)
            ImageAssetDiscovery.Candidate(file.name, 32, 32, file.length(), id.digest)
        }
        val queue = ImageAssetDiscovery.Queue(root, candidates)
        val report = ImageBatchRender.run(queue, drafts, stage,
            encode = { file, _, _, _, out, _ ->
                out.write(byteArrayOf(2, 3))
                if (file.name == "b.dds") file.writeBytes(byteArrayOf(4))
            }, verify = { _, _, _ -> })
        assertEquals(listOf(ImageBatchRender.State.STAGED, ImageBatchRender.State.FAILED, ImageBatchRender.State.SKIPPED),
            report.entries.map { it.state })
        val artifact = requireNotNull(report.entries.first().artifact)
        assertArrayEquals(byteArrayOf(2, 3), ImageBatchRender.readVerified(artifact))
        assertArrayEquals(byteArrayOf(1), files.first().readBytes())
        assertEquals(1, stage.listFiles()!!.size)
        artifact.file.writeBytes(byteArrayOf(5))
        assertTrue(runCatching { ImageBatchRender.readVerified(artifact) }.isFailure)
        ImageBatchRender.discard(report)
        assertEquals(0, stage.listFiles()!!.size)
    }
}
