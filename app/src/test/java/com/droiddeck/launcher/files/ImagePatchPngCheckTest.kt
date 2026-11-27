package com.droiddeck.launcher.files

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class ImagePatchPngCheckTest {
    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private fun chunk(type: String, data: ByteArray): ByteArray = ByteArrayOutputStream().also { buffer ->
        DataOutputStream(buffer).use { out ->
            val name = type.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply { update(name); update(data) }
            out.writeInt(data.size); out.write(name); out.write(data); out.writeInt(crc.value.toInt())
        }
    }.toByteArray()
    private val header = chunk("IHDR", byteArrayOf(0,0,0,1,0,0,0,1,8,6,0,0,0))
    private val data = ByteArrayOutputStream().also { buffer ->
        DeflaterOutputStream(buffer).use { it.write(byteArrayOf(0,0,0,0,0)) }
    }.toByteArray()
    private val end = chunk("IEND", byteArrayOf())
    private fun valid() = signature + header + chunk("IDAT", data) + end
    private fun rejects(bytes: ByteArray) = assertTrue(runCatching {
        ImagePatchPngCheck.validate(bytes, 1, 1)
    }.isFailure)

    @Test fun verifiesStaticPngAndDimensions() {
        ImagePatchPngCheck.validate(valid(), 1, 1)
        ImagePatchPngCheck.validate(signature + header + chunk("tEXt", byteArrayOf(65,0,66)) +
            chunk("IDAT", data) + end, 1, 1)
        assertTrue(runCatching { ImagePatchPngCheck.validate(valid(), 2, 1) }.isFailure)
    }

    @Test fun rejectsCorruptionAndNonstaticContainers() {
        val bytes = valid()
        for (i in bytes.indices) rejects(bytes.copyOf(i))
        rejects(bytes + byteArrayOf(0))
        rejects(bytes.copyOf().apply { this[20] = (this[20].toInt() xor 1).toByte() })
        rejects(signature + header + end)
        rejects(signature + header + chunk("IDAT", byteArrayOf()) + end)
        rejects(signature + header + chunk("acTL", ByteArray(8)) + chunk("IDAT", data) + end)
        rejects(signature + header + chunk("ABCD", byteArrayOf()) + chunk("IDAT", data) + end)
        rejects(signature + header + chunk("IDAT", data) + chunk("tEXt", byteArrayOf(65,0,66)) +
            chunk("IDAT", data) + end)
    }

    @Test fun cancellationIsNotSwallowed() {
        assertTrue(runCatching { ImagePatchPngCheck.validate(valid(), 1, 1) {
            throw CancellationException()
        } }.exceptionOrNull() is CancellationException)
    }
}
