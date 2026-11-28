package com.droiddeck.launcher.files

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationWorklistExportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun completeDraftsExportWithRelativePathsAndDoNotModifySources() {
        val root = temporary.newFolder("game")
        val drafts = temporary.newFolder("drafts")
        val source = """{"a":"Hello {name}"}"""
        val first = File(root, "a.json").apply { writeText(source) }
        val second = File(root, "nested/b.json").apply { parentFile!!.mkdirs(); writeText(source) }
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        for (file in listOf(first, second)) {
            TranslationDraftStore.save(drafts, file.canonicalPath, source, mapOf("a" to "안녕 {name}"))
        }
        val plan = TranslationWorklistExport.prepare(queue, drafts)
        TranslationWorklistExport.recheck(plan, queue, drafts)
        val bytes = ByteArrayOutputStream().also { plan.writeTo(it) }.toByteArray()
        val entries = linkedMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        assertEquals(setOf("files/a.json", "files/nested/b.json", "manifest.tsv"), entries.keys)
        assertEquals("안녕 {name}", GamePatchAuthor.extract(entries.getValue("files/a.json")).single().text)
        assertTrue(entries.getValue("manifest.tsv").contains(queue.files.first().digest))
        assertEquals(source, first.readText())
        assertEquals(source, second.readText())
        TranslationDraftStore.save(drafts, second.canonicalPath, source, mapOf("a" to "반가워요 {name}"))
        assertTrue(runCatching { TranslationWorklistExport.recheck(plan, queue, drafts) }.isFailure)
        first.writeText("""{"a":"Other"}""")
        assertTrue(runCatching { TranslationWorklistExport.prepare(queue, drafts) }.isFailure)
    }

    @Test fun refusesIncompleteOrInvalidSavedDrafts() {
        val root = temporary.newFolder("game")
        val drafts = temporary.newFolder("drafts")
        val source = """{"a":"Hello {name}","b":"Bye"}"""
        val file = File(root, "a.json").apply { writeText(source) }
        val queue = TranslationWorklist.Queue(root, TranslationDiscovery.inspect(root).candidates)
        assertTrue(runCatching { TranslationWorklistExport.prepare(queue, drafts) }.isFailure)
        TranslationDraftStore.save(drafts, file.canonicalPath, source, mapOf("a" to "안녕 {name}"))
        assertTrue(runCatching { TranslationWorklistExport.prepare(queue, drafts) }.isFailure)
        TranslationDraftStore.save(drafts, file.canonicalPath, source, mapOf("a" to "안녕", "b" to "잘 가"))
        assertTrue(runCatching { TranslationWorklistExport.prepare(queue, drafts) }.isFailure)
        assertEquals(source, file.readText())
    }
}
