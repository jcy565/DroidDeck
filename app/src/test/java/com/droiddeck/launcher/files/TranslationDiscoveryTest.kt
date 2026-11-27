package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TranslationDiscoveryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun findsOnlySupportedStructuresWithoutChangingFiles() {
        val root = temporary.newFolder("game")
        val strings = File(root, "strings").apply { mkdir() }
        val files = mapOf(
            File(strings, "one.csv") to "id,text\na,Hello\n",
            File(strings, "two.tsv") to "id\ttext\na\tWelcome\nb\tBye\n",
            File(root, "three.json") to """{"a":"Hello"}""",
            File(root, "wrong.csv") to "name,value\nx,1",
            File(root, "nested.json") to """{"a":{"b":"Hello"}}""",
            File(root, "bad.json") to "{\"a\":\"unterminated",
            File(root, "wrong.tsv") to "id,text\na,Hello",
            File(root, "ignore.ini") to "[game]\na=1"
        )
        files.forEach { (file, text) -> file.writeText(text) }
        val report = TranslationDiscovery.inspect(root)
        assertFalse(report.partial)
        assertEquals(7, report.examined)
        assertEquals(4, report.rejected)
        assertEquals(mapOf("strings/one.csv" to 1, "strings/two.tsv" to 2, "three.json" to 1),
            report.candidates.associate { it.relative to it.rows })
        files.forEach { (file, text) -> assertEquals(text, file.readText()) }
        val candidate = report.candidates.first()
        assertEquals(File(root, candidate.relative).canonicalFile, TranslationDiscovery.open(root, candidate))
    }

    @Test fun staleRevisionAndUnsafePathsAreRejected() {
        val root = temporary.newFolder("game")
        val source = File(root, "text.json").apply { writeText("""{"a":"Hello"}""") }
        val candidate = TranslationDiscovery.inspect(root).candidates.single()
        source.writeText("""{"a":"Other"}""")
        assertTrue(runCatching { TranslationDiscovery.open(root, candidate) }.isFailure)
        assertTrue(runCatching { TranslationDiscovery.open(root, candidate.copy(relative = "../text.json")) }.isFailure)
        source.delete()
        val external = temporary.newFile("external.json").apply { writeText("""{"a":"Hello"}""") }
        Files.createSymbolicLink(source.toPath(), external.toPath())
        assertTrue(TranslationDiscovery.inspect(root).candidates.isEmpty())
        assertTrue(runCatching { TranslationDiscovery.open(root, candidate) }.isFailure)
        assertEquals("""{"a":"Hello"}""", external.readText())
    }

    @Test fun traversalProbeResultLimitsAndCancellation() {
        val root = temporary.newFolder("game")
        repeat(4) { File(root, "$it.json").writeText("""{"a":"Hello"}""") }
        assertTrue(TranslationDiscovery.inspect(root, visitLimit = 1).partial)
        val probe = TranslationDiscovery.inspect(root, probeLimit = 2)
        assertTrue(probe.partial)
        assertEquals(2, probe.examined)
        val results = TranslationDiscovery.inspect(root, resultLimit = 1)
        assertTrue(results.partial)
        assertEquals(1, results.candidates.size)
        assertTrue(runCatching { TranslationDiscovery.inspect(root) {
            throw CancellationException("cancel")
        } }.exceptionOrNull() is CancellationException)
    }

    @Test fun oversizedAndInvalidUtf8FilesAreExcluded() {
        val root = temporary.newFolder("game")
        File(root, "large.json").writeBytes(ByteArray(TranslationLimits.MAX_PATCH_BYTES + 1))
        File(root, "broken.csv").writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        val report = TranslationDiscovery.inspect(root)
        assertEquals(2, report.examined)
        assertEquals(2, report.rejected)
        assertTrue(report.candidates.isEmpty())
    }
}
