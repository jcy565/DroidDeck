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

    @Test fun checksummedRecordsRejectDamageAndMergePreservesCorruptedBytes() {
        val root = temp.newFolder("game").canonicalFile
        val storage = temp.newFolder("storage")
        val candidate = ImageAssetDiscovery.Candidate("menu.png", 32, 16, 3, "a".repeat(64))
        val queue = ImageAssetDiscovery.Queue(root, listOf(candidate))
        ImageQueueStore.save(storage, queue)
        val record = storage.listFiles()!!.single()
        val original = record.readBytes()
        assertEquals("DDIMAGEQUEUE2", java.io.DataInputStream(original.inputStream()).use { it.readUTF() })
        assertEquals(queue, ImageQueueStore.load(storage, root))
        for (index in original.indices) {
            val damaged = original.clone().also { it[index] = (it[index].toInt() xor 1).toByte() }
            record.writeBytes(damaged)
            assertTrue("corruption at $index", runCatching { ImageQueueStore.load(storage, root) }.isFailure)
            record.writeBytes(original.copyOf(index))
            assertTrue("truncation at $index", runCatching { ImageQueueStore.load(storage, root) }.isFailure)
        }
        record.writeBytes(original + byteArrayOf(0))
        assertTrue(runCatching { ImageQueueStore.load(storage, root) }.isFailure)
        val damaged = original.clone().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        record.writeBytes(damaged)
        assertTrue(runCatching { ImageQueueStore.merge(storage, queue) }.isFailure)
        assertArrayEquals(damaged, record.readBytes())
    }

    @Test fun legacyQueueRemainsReadableAndUpgradesOnlyOnExplicitSave() {
        val root = temp.newFolder("game").canonicalFile
        val storage = temp.newFolder("storage")
        val candidate = ImageAssetDiscovery.Candidate("menu.png", 32, 16, 3, "a".repeat(64))
        val queue = ImageAssetDiscovery.Queue(root, listOf(candidate))
        ImageQueueStore.save(storage, queue)
        val record = storage.listFiles()!!.single()
        val legacy = java.io.ByteArrayOutputStream().also { buffer ->
            java.io.DataOutputStream(buffer).use { out ->
                out.writeUTF("DDIMAGEQUEUE1"); out.writeUTF(root.path); out.writeInt(1)
                out.writeUTF(candidate.relative); out.writeInt(candidate.width); out.writeInt(candidate.height)
                out.writeLong(candidate.bytes); out.writeUTF(candidate.digest)
            }
        }.toByteArray()
        record.writeBytes(legacy)
        assertEquals(queue, ImageQueueStore.load(storage, root))
        assertArrayEquals(legacy, record.readBytes())
        val expanded = queue.copy(candidates = listOf(candidate, candidate.copy(relative = "other.dds")))
        assertEquals(expanded, ImageQueueStore.merge(storage, expanded))
        assertEquals(expanded, ImageQueueStore.load(storage, root))
        assertEquals("DDIMAGEQUEUE2", java.io.DataInputStream(record.inputStream()).use { it.readUTF() })
        assertEquals(1, storage.listFiles()!!.size)
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun discoveryAndStorageUseTheSameSafeRelativePaths() {
        val root = temp.newFolder("game")
        val storage = temp.newFolder("storage")
        File(root, "bad\nname.png").writeBytes(byteArrayOf(1))
        File(root, "bad\\name.png").writeBytes(byteArrayOf(2))
        val normal = File(root, "menu.png").apply { writeBytes(byteArrayOf(3)) }
        val report = ImageAssetDiscovery.inspect(root, probe = { _, _ -> ImageAssetDiscovery.Size(32, 16) })
        assertTrue(report.partial)
        assertEquals(listOf("menu.png"), report.candidates.map { it.relative })
        val queue = ImageAssetDiscovery.Queue(root, report.candidates)
        ImageQueueStore.save(storage, queue)
        assertEquals(queue.copy(root = root.canonicalFile), ImageQueueStore.load(storage, root))
        assertEquals(normal.canonicalFile, ImageAssetDiscovery.open(root, queue.candidates.single()))
        val invalid = queue.candidates.single().copy(relative = "bad\nname.png")
        assertTrue(runCatching { ImageAssetDiscovery.open(root, invalid) }.isFailure)
    }

    @Test fun discoveryUsesOriginalSkipSnapshotAcrossCheckpoints() {
        val root = temp.newFolder("game")
        File(root, "a.png").writeBytes(byteArrayOf(1))
        File(root, "b.png").writeBytes(byteArrayOf(2))
        val skip = mutableSetOf("a.png")
        val report = ImageAssetDiscovery.inspect(root,
            probe = { _, _ -> ImageAssetDiscovery.Size(32, 16) },
            skip = skip, checkpoint = { skip.clear(); skip.add("b.png") })
        assertEquals(setOf("b.png"), skip)
        assertEquals(listOf("b.png"), report.candidates.map { it.relative })
        assertEquals(setOf("b.png"), report.examinedPaths)
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
