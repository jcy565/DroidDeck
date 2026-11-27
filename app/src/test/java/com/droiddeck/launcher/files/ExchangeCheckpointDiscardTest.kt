package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExchangeCheckpointDiscardTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun saved() = ExchangeCheckpoint.Saved(UUID.randomUUID().toString(), "Translate", 0, "a".repeat(64))

    @Test fun missingAndMalformedCheckpoint() {
        val file = File(temporary.root, "checkpoint.bin")
        assertFalse(ExchangeCheckpoint.discard(file))
        file.writeText("malformed")
        assertTrue(runCatching { ExchangeCheckpoint.load(file) }.isFailure)
        assertTrue(runCatching { ExchangeCheckpoint.clear(file, saved().id) }.isFailure)
        assertTrue(file.exists())
        assertTrue(ExchangeCheckpoint.discard(file))
        assertNull(ExchangeCheckpoint.load(file))
        val next = saved()
        ExchangeCheckpoint.save(file, next)
        assertEquals(next, ExchangeCheckpoint.load(file))
    }

    @Test fun wrongIdDoesNotClearValidCheckpoint() {
        val file = File(temporary.root, "checkpoint.bin")
        val value = saved()
        ExchangeCheckpoint.save(file, value)
        assertFalse(ExchangeCheckpoint.clear(file, UUID.randomUUID().toString()))
        assertEquals(value, ExchangeCheckpoint.load(file))
        assertTrue(ExchangeCheckpoint.discard(file))
        assertFalse(file.exists())
    }

    @Test fun discardRejectsDirectoriesAndSymlinks() {
        val directory = temporary.newFolder("not-a-file")
        assertTrue(runCatching { ExchangeCheckpoint.discard(directory) }.isFailure)
        assertTrue(directory.isDirectory)
        val target = temporary.newFile("target")
        target.writeText("preserve")
        val link = File(temporary.root, "checkpoint.bin")
        Files.createSymbolicLink(link.toPath(), target.toPath())
        assertTrue(runCatching { ExchangeCheckpoint.discard(link) }.isFailure)
        assertTrue(Files.isSymbolicLink(link.toPath()))
        assertEquals("preserve", target.readText())
        val parentLink = File(temporary.root, "linked-parent")
        Files.createSymbolicLink(parentLink.toPath(), directory.toPath())
        assertTrue(runCatching { ExchangeCheckpoint.discard(File(parentLink, "checkpoint.bin")) }.isFailure)
    }
}
