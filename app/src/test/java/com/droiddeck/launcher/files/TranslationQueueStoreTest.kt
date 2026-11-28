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

    @Test fun reopenedQueueResumesDraftsAndExportsOnlyReviewedChanges() {
        val root = temp.newFolder().canonicalFile
        val storage = temp.newFolder().canonicalFile
        val drafts = temp.newFolder().canonicalFile
        val tsv = "id\ttext\na\tStart {name}\nb\tExit\n"
        val json = """{"a":"Continue"}"""
        val a = File(root, "a.tsv").apply { writeText(tsv) }
        val b = File(root, "b.json").apply { writeText(json) }
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        TranslationQueueStore.save(storage, queue)
        TranslationDraftStore.save(drafts, a.canonicalPath, tsv, mapOf("a" to "시작 {name}"))
        val resumed = requireNotNull(TranslationQueueStore.load(storage, root))
        val report = TranslationWorklist.review(resumed, drafts)
        assertEquals(1, report.readyRows)
        assertEquals(3, report.totalRows)
        assertEquals("a.tsv", report.next()?.candidate?.relative)

        val edits = mapOf("a" to "시작 {name}", "b" to "종료")
        TranslationDraftStore.save(drafts, a.canonicalPath, tsv, edits)
        val complete = TranslationWorklist.review(resumed, drafts)
        assertEquals(2, complete.readyRows)
        assertEquals("b.json", complete.next()?.candidate?.relative)
        val snapshot = TranslationDiscovery.snapshot(resumed.root, resumed.files.first())
        val preview = GamePatchAuthor.translation(snapshot.source,
            requireNotNull(TranslationDraftStore.load(drafts, snapshot.file.canonicalPath, snapshot.source)))
        val zip = GamePatchAuthor.zip(snapshot.file.name, preview)
        java.util.zip.ZipInputStream(zip.inputStream()).use { input ->
            assertEquals("a.tsv", input.nextEntry.name)
            assertEquals("id\ttext\na\t시작 {name}\nb\t종료\n",
                input.readBytes().toString(Charsets.UTF_8))
            assertNull(input.nextEntry)
        }
        assertEquals(tsv, a.readText())
        assertEquals(json, b.readText())
        b.writeText("""{"a":"Changed"}""")
        val changed = TranslationWorklist.review(resumed, drafts)
        assertEquals(TranslationWorklist.State.SOURCE_CHANGED, changed.entries.last().state)
        assertNull(changed.next())
        assertEquals(resumed, TranslationQueueStore.load(storage, root))
    }

    @Test fun cancellationBeforePublicationPreservesPreviousQueue() {
        val root = temp.newFolder().canonicalFile
        val storage = temp.newFolder().canonicalFile
        File(root, "a.tsv").writeText("id\ttext\na\tStart\n")
        File(root, "b.tsv").writeText("id\ttext\nb\tExit\n")
        val all = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        val first = all.copy(files = all.files.take(1))
        TranslationQueueStore.save(storage, first)
        val old = storage.listFiles()!!.single().readBytes()
        var checks = 0
        val failure = runCatching {
            TranslationQueueStore.save(storage, all) {
                if (++checks == all.files.size + 1) throw java.util.concurrent.CancellationException()
            }
        }.exceptionOrNull()
        assertTrue(failure is java.util.concurrent.CancellationException)
        assertEquals(first, TranslationQueueStore.load(storage, root))
        assertArrayEquals(old, storage.listFiles()!!.single().readBytes())
        assertEquals(1, storage.listFiles()!!.size)
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
