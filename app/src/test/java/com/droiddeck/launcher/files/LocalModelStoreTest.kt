package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest
import java.nio.file.Files

class LocalModelStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun checksumPinnedImportAndVerification() {
        val dir = temporary.newFolder()
        val payload = "model fixture".toByteArray()
        val hash = digest(payload)
        val installed = LocalModelStore.install(dir, hash, payload.inputStream()) { true }
        assertArrayEquals(payload, installed.readBytes())
        assertEquals(installed, LocalModelStore.verified(dir, hash.uppercase()) { true })
        assertEquals(installed, LocalModelStore.install(dir, hash, "bad".byteInputStream()) { true })
        installed.writeText("corrupt")
        assertTrue(runCatching { LocalModelStore.verified(dir, hash) { true } }.isFailure)
    }

    @Test fun badDigestAndBudgetLeaveNoTemporaryFiles() {
        val dir = temporary.newFolder()
        val payload = "model fixture".toByteArray()
        val hash = digest(payload)
        assertTrue(runCatching { LocalModelStore.install(dir, hash, "bad".byteInputStream()) { true } }.isFailure)
        assertTrue(runCatching { LocalModelStore.install(dir, hash, payload.inputStream()) { false } }.isFailure)
        assertTrue(runCatching { LocalModelStore.install(dir, "../bad", payload.inputStream()) { true } }.isFailure)
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test fun budgetChangeDuringFinalReadRejectsImportAndVerification() {
        val dir = temporary.newFolder()
        val payload = "model fixture".toByteArray()
        val hash = digest(payload)
        var allowed = true
        val stream = object : java.io.ByteArrayInputStream(payload) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = super.read(b, off, len)
                if (count == -1) allowed = false
                return count
            }
        }
        assertTrue(runCatching { LocalModelStore.install(dir, hash, stream) { allowed } }.isFailure)
        assertTrue(dir.listFiles()!!.isEmpty())
        val installed = LocalModelStore.install(dir, hash, payload.inputStream()) { true }
        var checks = 0
        assertTrue(runCatching { LocalModelStore.verified(dir, hash) { ++checks < 4 } }.isFailure)
        assertArrayEquals(payload, installed.readBytes())
        assertEquals(installed, LocalModelStore.verified(dir, hash) { true })
    }

    @Test fun capacityAndSymlinkGuard() {
        val dir = temporary.newFolder()
        val a = "first".toByteArray()
        val b = "second".toByteArray()
        val c = "third".toByteArray()
        val one = LocalModelStore.install(dir, digest(a), a.inputStream()) { true }
        val two = LocalModelStore.install(dir, digest(b), b.inputStream()) { true }
        assertTrue(runCatching { LocalModelStore.install(dir, digest(c), c.inputStream()) { true } }.isFailure)
        assertTrue(one.delete())
        Files.createSymbolicLink(one.toPath(), two.toPath())
        assertTrue(runCatching { LocalModelStore.verified(dir, digest(a)) { true } }.isFailure)
        assertArrayEquals(b, two.readBytes())
    }
}
