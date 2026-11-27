package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class TgaImageTest {
    private fun sample(depth: Int, descriptor: Int): ByteArray {
        val header = ByteArray(18)
        header[2] = 2
        header[12] = 2
        header[14] = 2
        header[16] = depth.toByte()
        header[17] = descriptor.toByte()
        val colors = listOf(0x10203040, 0x50607080, 0x90a0b0c0.toInt(), 0xd0e0f001.toInt())
        return header + colors.flatMap { c ->
            (0 until depth / 8).map { shift -> (c ushr (shift * 8)).toByte() }
        }.toByteArray()
    }

    @Test fun decodesAllOriginsAndAlpha() {
        val top = TgaImage.decode(sample(32, 40), 1).argb
        assertArrayEquals(intArrayOf(0x10203040, 0x50607080, 0x90a0b0c0.toInt(), 0xd0e0f001.toInt()), top)
        assertArrayEquals(intArrayOf(top[2], top[3], top[0], top[1]), TgaImage.decode(sample(32, 8), 1).argb)
        assertArrayEquals(intArrayOf(top[1], top[0], top[3], top[2]), TgaImage.decode(sample(32, 56), 1).argb)
        assertArrayEquals(intArrayOf(top[3], top[2], top[1], top[0]), TgaImage.decode(sample(32, 24), 1).argb)
        assertArrayEquals(top.map { it or 0xff000000.toInt() }.toIntArray(), TgaImage.decode(sample(24, 32), 1).argb)
        assertArrayEquals(intArrayOf(top[0]), TgaImage.decode(sample(32, 40), 2).argb)
    }

    @Test fun readsRleAndWritesUncompressedCopy() {
        val raw = sample(32, 40)
        val compressed = raw.copyOfRange(0, 18).apply { this[2] = 10 } +
            byteArrayOf(3) + raw.copyOfRange(18, raw.size)
        val image = TgaImage.decode(compressed, 1)
        assertArrayEquals(TgaImage.decode(raw, 1).argb, image.argb)
        val output = java.io.ByteArrayOutputStream()
        TgaImage.writeCopy(compressed, 2, 2, output, { y, row ->
            image.argb.copyInto(row, 0, y * 2, (y + 1) * 2)
        })
        assertArrayEquals(raw, output.toByteArray())
        val run = compressed.copyOfRange(0, 18) + byteArrayOf(0x83.toByte(), 1, 2, 3, 4)
        assertTrue(TgaImage.decode(run, 1).argb.all { it == 0x04030201 })
        assertTrue(runCatching { TgaImage.inspect(run.copyOf().apply { this[18] = 0x84.toByte() }) }.isFailure)
    }

    @Test fun rejectsUnsupportedOrTruncatedAssets() {
        val bytes = sample(32, 40)
        for (bad in listOf(bytes.copyOf(17), bytes.copyOf(bytes.size - 1),
            bytes + byteArrayOf(1), bytes.copyOf().apply { this[2] = 10 },
            bytes.copyOf().apply { this[17] = 32 }, bytes.copyOf().apply { this[12] = 0 })) {
            assertTrue(runCatching { TgaImage.inspect(bad) }.isFailure)
        }
        assertTrue(runCatching { TgaImage.decode(bytes, 3) }.isFailure)
        val footer = ByteArray(8) + "TRUEVISION-XFILE.\u0000".toByteArray(Charsets.US_ASCII)
        assertEquals(2, TgaImage.inspect(bytes + footer).width)
        assertTrue(runCatching { TgaImage.inspect(bytes + footer.copyOf().apply { this[0] = 1 }) }.isFailure)
    }
}
