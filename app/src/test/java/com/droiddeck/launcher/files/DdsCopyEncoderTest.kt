package com.droiddeck.launcher.files

import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class DdsCopyEncoderTest {
    private fun fixture(f: DdsImage.Format, w: Int, h: Int, block: ByteArray): ByteArray {
        val a = ByteArray(128 + ((w + 3) / 4) * ((h + 3) / 4) * f.blockBytes)
        fun put(p: Int, v: Int) { for (i in 0..3) a[p + i] = (v ushr (8 * i)).toByte() }
        put(0, 0x20534444); put(4, 124); put(8, 0x81007); put(12, h); put(16, w)
        put(20, a.size - 128); put(76, 32); put(80, 4)
        put(84, when (f) {
            DdsImage.Format.DXT1 -> 0x31545844
            DdsImage.Format.DXT3 -> 0x33545844
            DdsImage.Format.DXT5 -> 0x35545844
        })
        put(108, 0x1000)
        for (p in 128 until a.size step block.size) block.copyInto(a, p)
        return a
    }

    @Test fun preservesUneditedBlocksAndHeader() {
        val red = 0xffff0000.toInt()
        val blue = 0xff0000ff.toInt()
        for (format in DdsImage.Format.values()) {
            val source = fixture(format, 7, 5, DdsCopyEncoder.encodeBlock(format, IntArray(16) { blue }))
            val output = ByteArrayOutputStream()
            DdsCopyEncoder.writeCopy(source, 7, 5, listOf(DdsCopyEncoder.Rect(0, 0, 4, 4)), output,
                { x, y, pixels -> assertEquals(0, x); assertEquals(0, y); pixels.fill(red) })
            val bytes = output.toByteArray()
            assertEquals(source.size, bytes.size)
            assertArrayEquals(source.copyOfRange(0, 128), bytes.copyOfRange(0, 128))
            assertArrayEquals(source.copyOfRange(128 + format.blockBytes, source.size),
                bytes.copyOfRange(128 + format.blockBytes, bytes.size))
            val decoded = DdsImage.decode(bytes, 1)
            for (y in 0..4) for (x in 0..6)
                assertEquals(if (x < 4 && y < 4) red else blue, decoded.argb[y * 7 + x])
        }
    }

    @Test fun rejectsChangesOutsideReviewedBlocksAndHeader() {
        for (format in DdsImage.Format.values()) {
            val source = fixture(format, 7, 5, DdsCopyEncoder.encodeBlock(format, IntArray(16) { 0xff0000ff.toInt() }))
            val regions = listOf(DdsCopyEncoder.Rect(0, 0, 4, 4))
            val output = ByteArrayOutputStream()
            DdsCopyEncoder.writeCopy(source, 7, 5, regions, output,
                { _, _, pixels -> pixels.fill(0xffff0000.toInt()) })
            val candidate = output.toByteArray()
            assertEquals(DdsCopyReview.Stats(4, 1, 1), DdsCopyReview.validate(source, candidate, regions))
            val changedHeader = candidate.copyOf().apply { this[32] = 1 }
            val changedOutside = candidate.copyOf().apply { this[128 + format.blockBytes] =
                (this[128 + format.blockBytes].toInt() xor 1).toByte() }
            assertTrue(runCatching { DdsCopyReview.validate(source, changedHeader, regions) }.isFailure)
            assertTrue(runCatching { DdsCopyReview.validate(source, changedOutside, regions) }.isFailure)
            assertTrue(runCatching { DdsCopyReview.validate(source, candidate, regions) { error("cancel") } }.isFailure)
        }
    }

    @Test fun handlesAlphaAndRejectsUnsupportedBc1PartialAlpha() {
        val f = DdsImage.Format.DXT1
        val p = IntArray(16) { if (it % 2 == 0) 0 else 0xffff0000.toInt() }
        assertArrayEquals(p, DdsImage.decode(fixture(f, 4, 4, DdsCopyEncoder.encodeBlock(f, p)), 1).argb)
        assertTrue(runCatching { DdsCopyEncoder.encodeBlock(f, IntArray(16) { 0x80ff0000.toInt() }) }.isFailure)
        for (format in listOf(DdsImage.Format.DXT3, DdsImage.Format.DXT5)) for (a in 0..255 step 17) {
            val color = (a shl 24) or 0xff0000
            assertTrue(DdsImage.decode(fixture(format, 4, 4,
                DdsCopyEncoder.encodeBlock(format, IntArray(16) { color })), 1).argb.all { it == color })
        }
    }
}
