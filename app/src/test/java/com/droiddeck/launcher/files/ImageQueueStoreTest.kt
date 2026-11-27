package com.droiddeck.launcher.files

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageQueueStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun savesAndRestoresSeparateRootQueues() {
        val root = temp.newFolder()
        val other = temp.newFolder()
        val dir = temp.newFolder()
        val c = ImageAssetDiscovery.Candidate("menu.png", 32, 16, 3, "a".repeat(64))
        val q = ImageAssetDiscovery.Queue(root, listOf(c))
        assertNull(ImageQueueStore.load(dir, root))
        ImageQueueStore.save(dir, q)
        assertEquals(q.copy(root = root.canonicalFile), ImageQueueStore.load(dir, root))
        assertNull(ImageQueueStore.load(dir, other))
        val changed = q.copy(candidates = listOf(c.copy(relative = "other.dds")))
        ImageQueueStore.save(dir, changed)
        assertEquals(changed.copy(root = root.canonicalFile), ImageQueueStore.load(dir, root))
        assertTrue(runCatching { ImageQueueStore.save(dir, q.copy(candidates = listOf(c.copy(relative = "../x.png")))) }.isFailure)
        assertEquals(changed.copy(root = root.canonicalFile), ImageQueueStore.load(dir, root))
    }

    @Test fun continuesWithoutReprobingExaminedFiles() {
        val root = temp.newFolder()
        repeat(13) { File(root, "$it.png").writeBytes(byteArrayOf(1)) }
        val probe: (ByteArray, String) -> ImageAssetDiscovery.Size = { _, _ -> ImageAssetDiscovery.Size(32, 16) }
        val first = ImageAssetDiscovery.inspect(root, probe = probe)
        val next = ImageAssetDiscovery.inspect(root, probe = probe, skip = first.examinedPaths)
        assertTrue(first.canContinue)
        assertEquals(8, first.candidates.size)
        assertEquals(5, next.candidates.size)
        assertTrue(first.examinedPaths.intersect(next.examinedPaths).isEmpty())
        assertFalse(next.canContinue)
    }
}
