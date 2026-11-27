package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationQueueStoreTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun persistsSelectedFilesAndKeepsSourceRevision() {
        val root = temp.newFolder().canonicalFile
        val storage = temp.newFolder().canonicalFile
        val source = File(root, "menu.tsv").apply { writeText("id\ttext\nmenu\tStart\n") }
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        assertNull(TranslationQueueStore.load(storage, root))
        TranslationQueueStore.save(storage, queue)
        assertEquals(queue, TranslationQueueStore.load(storage, root))
        source.writeText("id\ttext\nmenu\tExit\n")
        assertTrue(runCatching { TranslationQueueStore.save(storage, queue) }.isFailure)
        assertEquals(queue, TranslationQueueStore.load(storage, root))
        assertTrue(runCatching { TranslationDiscovery.open(root, queue.files.single()) }.isFailure)
        assertEquals("id\ttext\nmenu\tExit\n", source.readText())
    }

    @Test fun refusesDamagedRecordAndOverlappingStorage() {
        val root = temp.newFolder().canonicalFile
        val storage = temp.newFolder().canonicalFile
        File(root, "menu.tsv").writeText("id\ttext\nmenu\tStart\n")
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        assertTrue(runCatching { TranslationQueueStore.save(File(root, "private"), queue) }.isFailure)
        TranslationQueueStore.save(storage, queue)
        val file = storage.listFiles()!!.single()
        file.appendBytes(byteArrayOf(1))
        assertTrue(runCatching { TranslationQueueStore.load(storage, root) }.isFailure)
        assertTrue(file.delete())
        Files.createSymbolicLink(file.toPath(), File(storage, "missing").toPath())
        assertTrue(runCatching { TranslationQueueStore.load(storage, root) }.isFailure)
        assertTrue(runCatching { TranslationQueueStore.save(storage, queue) }.isFailure)
    }
}
