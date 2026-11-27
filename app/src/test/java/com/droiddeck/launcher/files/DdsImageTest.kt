package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class DdsImageTest {
    private fun fixture(w: Int, h: Int, format: DdsImage.Format, block: ByteArray): ByteArray {
        val a = ByteArray(128 + ((w + 3) / 4) * ((h + 3) / 4) * format.blockBytes)
        fun put(p: Int, v: Int) { for (i in 0..3) a[p + i] = (v ushr (i * 8)).toByte() }
        put(0, 0x20534444); put(4, 124); put(8, 0x81007); put(12, h); put(16, w)
        put(20, a.size - 128); put(76, 32); put(80, 4)
        put(84, when (format) {
            DdsImage.Format.DXT1 -> 0x31545844
            DdsImage.Format.DXT3 -> 0x33545844
            DdsImage.Format.DXT5 -> 0x35545844
        })
        put(108, 0x1000)
        for (p in 128 until a.size step block.size) block.copyInto(a, p)
        return a
    }

    private val color = byteArrayOf(0, 0xf8.toByte(), 31, 0, 0xe4.toByte(), 0xe4.toByte(), 0xe4.toByte(), 0xe4.toByte())

    @Test fun decodesColorAndAlphaModes() {
        val red = 0xffff0000.toInt()
        val blue = 0xff0000ff.toInt()
        val bc1 = fixture(4, 4, DdsImage.Format.DXT1, color)
        assertEquals(listOf(red, blue, 0xffaa0055.toInt(), 0xff5500aa.toInt()), DdsImage.decode(bc1, 1).argb.slice(0..3))
        val transparent = fixture(4, 4, DdsImage.Format.DXT1, byteArrayOf(31, 0, 0, 0xf8.toByte(), -1, -1, -1, -1))
        assertTrue(DdsImage.decode(transparent, 1).argb.all { it == 0 })
        val bc2 = fixture(4, 4, DdsImage.Format.DXT3, ByteArray(8) { 0xf0.toByte() } + color)
        assertEquals(listOf(0xff0000, blue, 0xaa0055, 0xff5500aa.toInt()), DdsImage.decode(bc2, 1).argb.slice(0..3))
        val bc3 = fixture(4, 4, DdsImage.Format.DXT5, byteArrayOf(210.toByte(), 0, 2, 0, 0, 0, 0, 0) + color)
        assertEquals(180, DdsImage.decode(bc3, 1).argb[0] ushr 24)
    }

    @Test fun handlesPartialBlocksAndRejectsInvalidInput() {
        val a = fixture(5, 7, DdsImage.Format.DXT1, color)
        val p = DdsImage.decode(a, 2)
        assertEquals(3, p.width)
        assertEquals(4, p.height)
        assertEquals(12, p.argb.size)
        for (bad in listOf(a.copyOf(127), a.copyOf(a.size - 1), a + 0.toByte(),
            a.copyOf().apply { this[28] = 2 }, a.copyOf().apply { this[112] = 2 },
            a.copyOf().apply { this[84] = 0 }, a.copyOf().apply { this[20] = 0 })) {
            assertTrue(runCatching { DdsImage.inspect(bad) }.isFailure)
        }
        assertTrue(runCatching { DdsImage.decode(a, 3) }.isFailure)
        assertTrue(runCatching { DdsImage.decode(a, 1) { error("cancel") } }.isFailure)
    }
}
