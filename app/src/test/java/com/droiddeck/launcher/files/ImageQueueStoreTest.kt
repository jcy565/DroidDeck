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

    @Test fun mergesPagesWithoutDuplicatesOrRevisionReplacement() {
        val root = temp.newFolder()
        val dir = temp.newFolder()
        val first = ImageAssetDiscovery.Candidate("a.png", 32, 16, 3, "a".repeat(64))
        val second = first.copy(relative = "b.dds")
        val one = ImageAssetDiscovery.Queue(root, listOf(first))
        val two = ImageAssetDiscovery.Queue(root, listOf(first, second))
        assertEquals(listOf(first), ImageQueueStore.merge(dir, one).candidates)
        assertEquals(listOf(first, second), ImageQueueStore.merge(dir, two).candidates)
        assertEquals(listOf(first, second), ImageQueueStore.merge(dir, two).candidates)
        val changed = one.copy(candidates = listOf(first.copy(digest = "b".repeat(64))))
        assertTrue(runCatching { ImageQueueStore.merge(dir, changed) }.isFailure)
        assertEquals(listOf(first, second), ImageQueueStore.load(dir, root)?.candidates)
        val tooMany = one.copy(candidates = (0..19).map { first.copy(relative = "x$it.png") })
        assertTrue(runCatching { ImageQueueStore.merge(dir, tooMany) }.isFailure)
        assertEquals(listOf(first, second), ImageQueueStore.load(dir, root)?.candidates)
        assertTrue(runCatching { ImageQueueStore.combine(one, two.copy(root = temp.newFolder())) }.isFailure)
    }

    @Test fun rejectsOverlappingStorageBeforeCreatingAnyFiles() {
        val root = temp.newFolder("game")
        val storage = temp.newFolder("storage")
        val c = ImageAssetDiscovery.Candidate("menu.png", 32, 16, 3, "a".repeat(64))
        val q = ImageAssetDiscovery.Queue(root, listOf(c))
        val nested = File(root, "private")
        for (dir in listOf(root, nested, root.parentFile!!)) {
            assertTrue(runCatching { ImageQueueStore.save(dir, q) }.isFailure)
            assertTrue(runCatching { ImageQueueStore.load(dir, root) }.isFailure)
            assertTrue(runCatching { ImageQueueStore.merge(dir, q) }.isFailure)
        }
        assertFalse(nested.exists())
        assertTrue(root.listFiles()!!.isEmpty())
        ImageQueueStore.save(storage, q)
        assertEquals(q.copy(root = root.canonicalFile), ImageQueueStore.load(storage, root))
        val prior = storage.listFiles()!!.single().readBytes()
        val invalid = q.copy(candidates = listOf(c.copy(relative = "bad\nname.png")))
        assertTrue(runCatching { ImageQueueStore.save(storage, invalid) }.isFailure)
        assertArrayEquals(prior, storage.listFiles()!!.single().readBytes())
    }

    @Test fun rejectsNonRegularQueueWithoutReplacingIt() {
        val root = temp.newFolder("game")
        val storage = temp.newFolder("storage")
        val c = ImageAssetDiscovery.Candidate("menu.png", 32, 16, 3, "a".repeat(64))
        val q = ImageAssetDiscovery.Queue(root, listOf(c))
        ImageQueueStore.save(storage, q)
        val record = storage.listFiles()!!.single()
        assertTrue(record.delete())
        assertTrue(record.mkdir())
        assertTrue(runCatching { ImageQueueStore.load(storage, root) }.isFailure)
        assertTrue(runCatching { ImageQueueStore.merge(storage, q) }.isFailure)
        assertTrue(runCatching { ImageQueueStore.save(storage, q) }.isFailure)
        assertTrue(record.isDirectory)
        assertTrue(record.delete())
        java.nio.file.Files.createSymbolicLink(record.toPath(), File(storage, "missing").toPath())
        assertTrue(runCatching { ImageQueueStore.load(storage, root) }.isFailure)
        assertTrue(runCatching { ImageQueueStore.save(storage, q) }.isFailure)
        assertTrue(java.nio.file.Files.isSymbolicLink(record.toPath()))
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
