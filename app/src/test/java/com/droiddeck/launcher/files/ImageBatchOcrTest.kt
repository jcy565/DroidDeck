package com.droiddeck.launcher.files

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageBatchOcrTest {
    @get:Rule val temp = TemporaryFolder()
    private val script = ImageTranslationInput.Script.LATIN
    private fun result() = ImageTextRegions.build("Start", 32, 32,
        sequenceOf(ImageTextRegions.Box(0, 0, 20, 20) to "Start"))
    private fun candidate(file: File) = ImageAssetDiscovery.Candidate(file.name, 32, 32,
        file.length(), ImageDraftStore.identity(file).digest)

    @Test fun skipsExistingAndPreservesChangedSource() = runBlocking {
        val root = temp.newFolder()
        val dir = temp.newFolder()
        val a = File(root, "a.png").apply { writeBytes(byteArrayOf(1)) }
        val b = File(root, "b.png").apply { writeBytes(byteArrayOf(2)) }
        val c = File(root, "c.png").apply { writeBytes(byteArrayOf(3)) }
        val q = ImageAssetDiscovery.Queue(root, listOf(a, b, c).map(::candidate))
        val manual = ImageDraftStore.Draft("Start", "시작", script, ImageTranslationInput.Rotation.NONE)
        ImageDraftStore.save(dir, a, ImageDraftStore.identity(a), manual)
        val called = mutableListOf<String>()
        val report = ImageBatchOcr.run(q, dir, script, recognize = { file, _ ->
            called.add(file.name)
            if (file == c) file.writeBytes(byteArrayOf(4))
            result()
        })
        assertEquals(listOf("b.png", "c.png"), called)
        assertEquals(listOf(ImageBatchOcr.State.EXISTING, ImageBatchOcr.State.SAVED, ImageBatchOcr.State.FAILED),
            report.entries.map { it.state })
        assertEquals(manual, ImageDraftStore.load(dir, ImageDraftStore.identity(a)))
        assertEquals(result(), ImageDraftStore.load(dir, ImageDraftStore.identity(b))?.regions)
        assertNull(ImageDraftStore.load(dir, ImageDraftStore.identity(c)))
    }

    @Test fun concurrentManualDraftWins() = runBlocking {
        val root = temp.newFolder()
        val dir = temp.newFolder()
        val file = File(root, "a.png").apply { writeBytes(byteArrayOf(1)) }
        val id = ImageDraftStore.identity(file)
        val q = ImageAssetDiscovery.Queue(root, listOf(candidate(file)))
        val manual = ImageDraftStore.Draft("Start", "시작", script, ImageTranslationInput.Rotation.NONE)
        val report = ImageBatchOcr.run(q, dir, script, recognize = { _, _ ->
            ImageDraftStore.save(dir, file, id, manual)
            result()
        })
        assertEquals(ImageBatchOcr.State.EXISTING, report.entries.single().state)
        assertEquals(manual, ImageDraftStore.load(dir, id))
    }
}
