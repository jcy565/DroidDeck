package com.droiddeck.launcher.files

import java.io.File
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationWorklistTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun reviewUsesOnlySavedStructurallyValidEdits() {
        val root = temporary.newFolder("game")
        val drafts = temporary.newFolder("drafts")
        val source = """{"a":"Hello {name}","b":"Bye"}"""
        val a = File(root, "a.json").apply { writeText(source) }
        val b = File(root, "b.json").apply { writeText(source) }
        val c = File(root, "c.json").apply { writeText(source) }
        TranslationDraftStore.save(drafts, a.canonicalPath, source, mapOf("a" to "안녕 {name}", "b" to "Bye"))
        TranslationDraftStore.save(drafts, b.canonicalPath, source, mapOf("a" to "안녕", "b" to "잘 가"))
        TranslationDraftStore.save(drafts, c.canonicalPath, source, mapOf("a" to "안녕 {name}", "b" to "잘 가"))
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        val report = TranslationWorklist.review(queue, drafts)
        assertEquals(6, report.totalRows)
        assertEquals(4, report.readyRows)
        assertEquals(listOf(TranslationWorklist.State.IN_PROGRESS, TranslationWorklist.State.IN_PROGRESS,
            TranslationWorklist.State.READY_FOR_REVIEW), report.entries.map { it.state })
        assertEquals("a.json", report.next()?.candidate?.relative)
        assertEquals(source, a.readText())
    }

    @Test fun changedAndCorruptFilesAreNotSelectedAsNext() {
        val root = temporary.newFolder("game")
        val drafts = temporary.newFolder("drafts")
        val source = """{"a":"Hello"}"""
        val a = File(root, "a.json").apply { writeText(source) }
        val b = File(root, "b.json").apply { writeText(source) }
        File(root, "c.json").writeText(source)
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        TranslationDraftStore.save(drafts, b.canonicalPath, source, mapOf("a" to "안녕"))
        drafts.listFiles()!!.single().writeText("damaged")
        a.writeText("""{"a":"Other"}""")
        val report = TranslationWorklist.review(queue, drafts)
        assertEquals(listOf(TranslationWorklist.State.SOURCE_CHANGED, TranslationWorklist.State.DRAFT_UNAVAILABLE,
            TranslationWorklist.State.NOT_STARTED), report.entries.map { it.state })
        assertEquals("c.json", report.next()?.candidate?.relative)
        assertEquals("damaged", drafts.listFiles()!!.single().readText())
        assertTrue(runCatching { TranslationWorklist.review(queue, drafts) { throw CancellationException() } }
            .exceptionOrNull() is CancellationException)
    }

    @Test fun reviewUsesOriginalCandidatesEvenWhenCallerMutatesSelection() {
        val root = temporary.newFolder("game")
        val drafts = temporary.newFolder("drafts")
        File(root, "a.json").writeText("""{"a":"Hello"}""")
        File(root, "b.json").writeText("""{"b":"Bye"}""")
        val original = TranslationDiscovery.inspect(root).candidates
        val selected = original.toMutableList()
        val queue = TranslationWorklist.Queue(root, selected)
        var checks = 0
        val report = TranslationWorklist.review(queue, drafts) {
            if (++checks == 1) selected.clear()
        }
        assertTrue(selected.isEmpty())
        assertEquals(original, report.entries.map { it.candidate })
        assertEquals(2, report.totalRows)
        assertTrue(report.entries.all { it.state == TranslationWorklist.State.NOT_STARTED })
        assertEquals(original.first(), report.next()?.candidate)
    }

    @Test fun queueRejectsEmptyDuplicateOrOversizedSelections() {
        val root = temporary.newFolder("game")
        val candidate = TranslationDiscovery.Candidate("a.json", 1, "0".repeat(64))
        assertTrue(runCatching { TranslationWorklist.Queue(root, emptyList()) }.isFailure)
        assertTrue(runCatching { TranslationWorklist.Queue(root, listOf(candidate, candidate)) }.isFailure)
        assertTrue(runCatching { TranslationWorklist.Queue(root, (1..21).map { candidate.copy(relative = "$it.json") }) }.isFailure)
    }
}
