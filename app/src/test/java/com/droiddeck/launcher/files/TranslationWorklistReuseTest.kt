package com.droiddeck.launcher.files

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationWorklistReuseTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun exactMatchesOnlyAndExplicitSavePreserveExistingEdits() {
        val root = temporary.newFolder("game")
        val drafts = temporary.newFolder("drafts")
        val source = """{"a":"Hello {name}","b":"Bye","c":"Other"}"""
        val target = File(root, "target.json").apply { writeText(source) }
        val donorSource = """{"x":"Hello {name}","y":"Bye","z":"Other!"}"""
        val donor = File(root, "donor.json").apply { writeText(donorSource) }
        TranslationDraftStore.save(drafts, donor.canonicalPath, donorSource,
            mapOf("x" to "안녕 {name}", "y" to "잘 가", "z" to "다른"))
        TranslationDraftStore.save(drafts, target.canonicalPath, source, mapOf("b" to "안녕히 가"))
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        val selected = queue.files.single { it.relative == target.name }
        val plan = TranslationWorklistReuse.prepare(queue, selected, drafts)
        assertEquals(listOf("a"), plan.suggestions.map { it.id })
        assertEquals(mapOf("b" to "안녕히 가"), TranslationDraftStore.load(drafts, target.canonicalPath, source))
        TranslationWorklistReuse.saveReviewed(queue, plan, drafts)
        assertEquals(mapOf("a" to "안녕 {name}", "b" to "안녕히 가"),
            TranslationDraftStore.load(drafts, target.canonicalPath, source))
        assertEquals(source, target.readText())
        assertEquals(donorSource, donor.readText())
    }

    @Test fun conflictingDonorsAndChangedDraftRefuseReuse() {
        val root = temporary.newFolder("game")
        val drafts = temporary.newFolder("drafts")
        val source = """{"a":"Hello {name}","b":"Bye"}"""
        val target = File(root, "target.json").apply { writeText(source) }
        val donor1 = File(root, "one.json").apply { writeText(source) }
        val donor2 = File(root, "two.json").apply { writeText(source) }
        TranslationDraftStore.save(drafts, donor1.canonicalPath, source, mapOf("a" to "안녕 {name}", "b" to "잘 가"))
        TranslationDraftStore.save(drafts, donor2.canonicalPath, source, mapOf("a" to "반가워 {name}"))
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        val selected = queue.files.single { it.relative == target.name }
        val plan = TranslationWorklistReuse.prepare(queue, selected, drafts)
        assertEquals(listOf("b"), plan.suggestions.map { it.id })
        assertEquals(1, plan.conflicts)
        TranslationDraftStore.save(drafts, target.canonicalPath, source, mapOf("b" to "안녕히 가"))
        assertTrue(runCatching { TranslationWorklistReuse.saveReviewed(queue, plan, drafts) }.isFailure)
        assertEquals(mapOf("b" to "안녕히 가"), TranslationDraftStore.load(drafts, target.canonicalPath, source))
    }
}
