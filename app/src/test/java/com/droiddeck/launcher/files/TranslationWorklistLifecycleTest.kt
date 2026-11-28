package com.droiddeck.launcher.files

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real parser, discovery, queue persistence, draft storage and review. */
class TranslationWorklistLifecycleTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun savedQueueCanResumeWithMixedSourceAndDraftStates() {
        val root = temp.newFolder("game")
        val storage = File(temp.root, "queue")
        val drafts = File(temp.root, "drafts")
        val sources = linkedMapOf(
            "a.csv" to "id,text\na,Hello {name}\nb,Bye",
            "b.tsv" to "id\ttext\na\tHello {name}\nb\tBye",
            "c.json" to """{"a":"Hello {name}","b":"Bye"}""",
            "d.json" to """{"a":"Hello {name}","b":"Bye"}""")
        sources.forEach { (name, source) -> File(root, name).writeText(source) }
        File(root, "invalid.json").writeText("[]")
        val discovered = TranslationDiscovery.inspect(root)
        assertEquals(5, discovered.examined)
        assertEquals(1, discovered.rejected)
        assertFalse(discovered.partial)
        val queue = TranslationWorklist.Queue(root, discovered.candidates)
        assertNull(TranslationQueueStore.load(storage, root))
        assertTrue(runCatching {
            TranslationQueueStore.load(storage, root) { throw CancellationException("cancel") }
        }.exceptionOrNull() is CancellationException)
        assertFalse(storage.exists())
        TranslationQueueStore.save(storage, queue)
        val record = storage.listFiles()!!.single()
        val originalRecord = record.readBytes()
        val loaded = requireNotNull(TranslationQueueStore.load(storage, root))
        assertEquals(queue, loaded)
        val initial = TranslationWorklist.review(loaded, drafts)
        assertEquals(8, initial.totalRows)
        assertEquals(0, initial.readyRows)
        assertTrue(initial.entries.all { it.state == TranslationWorklist.State.NOT_STARTED })

        fun save(name: String, edits: Map<String, String>) {
            TranslationDraftStore.save(drafts, File(root, name).canonicalPath, sources.getValue(name), edits)
        }
        save("a.csv", mapOf("a" to "안녕 {name}", "b" to "잘 가"))
        save("b.tsv", mapOf("a" to "안녕 {name}"))
        save("c.json", mapOf("a" to "invalid"))
        val previous = drafts.listFiles()!!.toSet()
        save("d.json", mapOf("a" to "안녕 {name}"))
        val damaged = (drafts.listFiles()!!.toSet() - previous).single()
        assertEquals(4, TranslationWorklist.review(loaded, drafts).readyRows)
        damaged.writeBytes(byteArrayOf(1, 2, 3))
        File(root, "c.json").writeText("""{"a":"Changed"}""")
        val report = TranslationWorklist.review(loaded, drafts)
        assertEquals(listOf(TranslationWorklist.State.READY_FOR_REVIEW,
            TranslationWorklist.State.IN_PROGRESS, TranslationWorklist.State.SOURCE_CHANGED,
            TranslationWorklist.State.DRAFT_UNAVAILABLE), report.entries.map { it.state })
        assertEquals(3, report.readyRows)
        assertEquals(8, report.totalRows)
        assertEquals("b.tsv", report.next()?.candidate?.relative)
        assertTrue(runCatching { TranslationQueueStore.save(storage, loaded) }.isFailure)
        assertArrayEquals(originalRecord, record.readBytes())
        assertEquals(loaded, TranslationQueueStore.load(storage, root))
        assertTrue(runCatching {
            TranslationWorklist.review(loaded, drafts) { throw CancellationException("cancel") }
        }.exceptionOrNull() is CancellationException)
        assertArrayEquals(byteArrayOf(1, 2, 3), damaged.readBytes())
        sources.filterKeys { it != "c.json" }.forEach { (name, source) ->
            assertEquals(source, File(root, name).readText())
        }
    }
}
