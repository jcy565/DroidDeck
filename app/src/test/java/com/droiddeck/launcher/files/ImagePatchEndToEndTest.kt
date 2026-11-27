package com.droiddeck.launcher.files

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Exercise the real container, inflater, archive, import and backup together. */
class ImagePatchEndToEndTest {
    @get:Rule val temp = TemporaryFolder()

    private fun png(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            fun chunk(name: String, bytes: ByteArray) {
                val type = name.toByteArray(Charsets.US_ASCII)
                d.writeInt(bytes.size); d.write(type); d.write(bytes)
                d.writeInt(CRC32().apply { update(type); update(bytes) }.value.toInt())
            }
            chunk("IHDR", byteArrayOf(0,0,0,1,0,0,0,1,8,6,0,0,0))
            val compressed = ByteArrayOutputStream()
            DeflaterOutputStream(compressed).use { it.write(raw) }
            chunk("IDAT", compressed.toByteArray())
            chunk("IEND", byteArrayOf())
        }
        return out.toByteArray()
    }
    private fun hash(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun actualPngArchiveBackupAndCancellationPreserveOriginals() {
        val game = temp.newFolder()
        val output = temp.newFolder()
        val store = temp.newFolder()
        val badStore = temp.newFolder()
        val original = png(byteArrayOf(0,0,0,0,-1))
        val translation = png(byteArrayOf(0,12,34,56,-1))
        val malformed = png(byteArrayOf(5,12,34,56,-1))
        val files = listOf(File(game, "one.png"), File(game, "two.png"))
        files.forEach { it.writeBytes(original) }
        val sources = files.map { ImagePatchArchive.Source(it.name, hash(original), original.size.toLong(), 1, 1) }
        val verify: (ByteArray, ImagePatchArchive.Source) -> Unit = { b, s ->
            ImagePatchPngCheck.validate(b, s.width, s.height)
        }
        val patch = ImagePatchArchive.create(game, output, sources, { translation })
        val report = ImagePatchImport.inspect(game, patch.file, verify)
        assertTrue(report.entries.all { it.state == ImagePatchImport.State.READY })
        assertNull(ImagePatchBackup.load(store, game))
        var calls = 0
        val backup = ImagePatchBackup.create(store, game, report, verify) { calls++ }
        assertEquals(backup, ImagePatchBackup.load(store, game))
        assertTrue(files.all { it.readBytes().contentEquals(original) })
        var saved = 0
        ImagePatchArchive.inspectPayloads(File(backup.directory, "originals.zip")) { row, bytes ->
            verify(bytes, row.source)
            assertEquals(row.source.originalHash, row.outputHash)
            assertArrayEquals(original, bytes)
            saved++
        }
        assertEquals(2, saved)
        val record = File(backup.directory, "recovery.ddib")
        val originalRecord = record.readBytes()
        assertTrue(runCatching { ImagePatchBackup.create(store, game, report, verify) }.isFailure)
        assertArrayEquals(originalRecord, record.readBytes())
        val invalid = ImagePatchArchive.create(game, output, sources, { malformed })
        val bad = ImagePatchImport.inspect(game, invalid.file, verify)
        assertTrue(bad.entries.all { it.state == ImagePatchImport.State.INVALID_OUTPUT })
        assertTrue(runCatching { ImagePatchBackup.create(badStore, game, bad, verify) }.isFailure)
        val same = ImagePatchArchive.create(game, output, sources, { original })
        val unchanged = ImagePatchImport.inspect(game, same.file, verify)
        assertTrue(unchanged.entries.all { it.state == ImagePatchImport.State.UNCHANGED })
        assertTrue(runCatching { ImagePatchBackup.create(badStore, game, unchanged, verify) }.isFailure)
        assertTrue(badStore.listFiles()!!.isEmpty())
        for (i in 1..calls) {
            var count = 0
            val err = runCatching {
                ImagePatchBackup.create(badStore, game, report, verify) {
                    if (++count == i) throw CancellationException()
                }
            }.exceptionOrNull()
            assertTrue("checkpoint $i", err is CancellationException)
            assertTrue(badStore.listFiles()!!.isEmpty())
            assertTrue(files.all { it.readBytes().contentEquals(original) })
        }
        record.writeBytes(originalRecord.copyOf(originalRecord.size - 1))
        assertTrue(runCatching { ImagePatchBackup.load(store, game) }.isFailure)
        record.writeBytes(originalRecord)
        assertEquals(backup, ImagePatchBackup.load(store, game))
    }
}
