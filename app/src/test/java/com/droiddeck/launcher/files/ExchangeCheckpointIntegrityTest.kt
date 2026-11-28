package com.droiddeck.launcher.files

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExchangeCheckpointIntegrityTest {
    @get:Rule val temp = TemporaryFolder()
    private fun saved() = ExchangeCheckpoint.Saved(UUID.randomUUID().toString(),
        "한국어로 번역 😀", 3, ExchangeCheckpoint.fingerprint("target", "source", emptyMap(), "prompt"))

    @Test fun checksummedRecordRejectsDamageAndRetainsLegacyCompatibility() {
        val file = File(temp.root, "request")
        val saved = saved()
        ExchangeCheckpoint.save(file, saved)
        val original = file.readBytes()
        assertEquals(saved, ExchangeCheckpoint.load(file))
        for (index in original.indices) {
            val damaged = original.clone().also { it[index] = (it[index].toInt() xor 1).toByte() }
            file.writeBytes(damaged)
            assertTrue(runCatching { ExchangeCheckpoint.load(file) }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.clear(file, saved.id) }.isFailure)
            assertArrayEquals(damaged, file.readBytes())
            file.writeBytes(original.copyOf(index))
            assertTrue(runCatching { ExchangeCheckpoint.load(file) }.isFailure)
        }
        file.writeBytes(original + byteArrayOf(0))
        assertTrue(runCatching { ExchangeCheckpoint.load(file) }.isFailure)
        val legacy = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use {
                it.writeInt(0x44445831); it.writeUTF(saved.id); it.writeUTF(saved.instruction)
                it.writeInt(saved.start); it.writeUTF(saved.fingerprint)
            }
        }.toByteArray()
        file.writeBytes(legacy)
        assertEquals(saved, ExchangeCheckpoint.load(file))
        assertArrayEquals(legacy, file.readBytes())
        ExchangeCheckpoint.save(file, saved)
        assertArrayEquals(original, file.readBytes())
        assertFalse(ExchangeCheckpoint.clear(file, UUID.randomUUID().toString()))
        assertTrue(ExchangeCheckpoint.clear(file, saved.id))
        assertFalse(file.exists())
    }

    @Test fun fingerprintsRejectMalformedUnicodeAndPreserveExistingValidHashes() {
        val file = File(temp.root, "request")
        val reviewed = linkedMapOf("b" to "둘", "a" to "하나")
        val expected = "1e037d93efca4f334b045acdcf25713c26551564e299c7cdfa9d61fcafc88e63"
        assertEquals(expected, ExchangeCheckpoint.fingerprint("target", "source", reviewed, "prompt"))
        assertEquals(expected, ExchangeCheckpoint.fingerprint("target", "source", reviewed.toSortedMap(), "prompt"))
        val inconsistent = object : Map<String, String> by reviewed {
            override fun get(key: String): String? = "unchecked"
        }
        assertEquals(expected, ExchangeCheckpoint.fingerprint("target", "source", inconsistent, "prompt"))
        val saved = saved()
        ExchangeCheckpoint.save(file, saved)
        val original = file.readBytes()
        for (code in 0xD800..0xDFFF) {
            val bad = "bad" + code.toChar()
            assertTrue(runCatching { ExchangeCheckpoint.fingerprint(bad, "source", reviewed, "prompt") }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.fingerprint("target", bad, reviewed, "prompt") }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.fingerprint("target", "source", reviewed, bad) }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.fingerprint("target", "source", mapOf(bad to "x"), "prompt") }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.fingerprint("target", "source", mapOf("x" to bad), "prompt") }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.save(file, saved.copy(instruction = bad)) }.isFailure)
            assertArrayEquals(original, file.readBytes())
        }
        assertEquals(saved, ExchangeCheckpoint.load(file))
    }

    @Test fun invalidParentAndLeafAreNotTreatedAsMissingOrOverwritten() {
        val invalid = temp.newFile().apply { writeText("keep") }
        val saved = saved()
        val destination = File(temp.root, "request")
        ExchangeCheckpoint.save(destination, saved)
        val original = destination.readBytes()
        val alias = File(temp.root, "alias")
        Files.createSymbolicLink(alias.toPath(), destination.toPath())
        for (bad in listOf(File(invalid, "request"), temp.newFolder(), alias)) {
            assertTrue(runCatching { ExchangeCheckpoint.load(bad) }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.save(bad, saved) }.isFailure)
            assertTrue(runCatching { ExchangeCheckpoint.discard(bad) }.isFailure)
        }
        assertEquals("keep", invalid.readText())
        assertArrayEquals(original, destination.readBytes())
        assertTrue(Files.isSymbolicLink(alias.toPath()))
    }
}
