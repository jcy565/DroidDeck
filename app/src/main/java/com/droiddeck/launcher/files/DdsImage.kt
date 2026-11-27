package com.droiddeck.launcher.files

/** Legacy single-level 2D BC1/BC2/BC3 textures. No DX10, arrays, cube maps or volume data. */
internal object DdsImage {
    enum class Format(val blockBytes: Int) { DXT1(8), DXT3(16), DXT5(16) }
    data class Info(val width: Int, val height: Int, val format: Format)
    data class Pixels(val width: Int, val height: Int, val argb: IntArray)

    private fun u(b: ByteArray, p: Int) = b[p].toInt() and 255
    private fun word(b: ByteArray, p: Int) = u(b, p) or (u(b, p + 1) shl 8)
    private fun dword(b: ByteArray, p: Int) = word(b, p) or (word(b, p + 2) shl 16)

    fun inspect(bytes: ByteArray): Info {
        require(bytes.size in 128..ImageTranslationInput.MAX_BYTES)
        require(dword(bytes, 0) == 0x20534444 && dword(bytes, 4) == 124 && dword(bytes, 76) == 32)
        val flags = dword(bytes, 8)
        require(flags and 0x1007 == 0x1007 && flags and 0x800000 == 0)
        val height = dword(bytes, 12)
        val width = dword(bytes, 16)
        ImageTranslationInput.sampleSize(width, height)
        require(width.toLong() * height <= 8_388_608L)
        require(dword(bytes, 24) in 0..1 && dword(bytes, 28) in 0..1)
        require(dword(bytes, 80) == 4 && dword(bytes, 108) == 0x1000 && dword(bytes, 112) == 0)
        require(dword(bytes, 116) == 0 && dword(bytes, 120) == 0)
        val format = when (dword(bytes, 84)) {
            0x31545844 -> Format.DXT1
            0x33545844 -> Format.DXT3
            0x35545844 -> Format.DXT5
            else -> error("DXT1·DXT3·DXT5 DDS만 지원합니다.")
        }
        val payload = ((width + 3L) / 4) * ((height + 3L) / 4) * format.blockBytes
        require(payload + 128 == bytes.size.toLong())
        if (flags and 0x80000 != 0) require(dword(bytes, 20).toLong() == payload)
        return Info(width, height, format)
    }

    private fun rgb565(c: Int): Int {
        val r = c ushr 11 and 31
        val g = c ushr 5 and 63
        val b = c and 31
        return 0xff000000.toInt() or (((r shl 3) or (r ushr 2)) shl 16) or
            (((g shl 2) or (g ushr 4)) shl 8) or ((b shl 3) or (b ushr 2))
    }

    private fun blend(a: Int, b: Int, wa: Int, wb: Int): Int {
        fun c(shift: Int) = (((a ushr shift and 255) * wa + (b ushr shift and 255) * wb) / (wa + wb)) shl shift
        return 0xff000000.toInt() or c(16) or c(8) or c(0)
    }

    fun decode(bytes: ByteArray, sample: Int, checkpoint: () -> Unit = {}): Pixels {
        val info = inspect(bytes)
        require(sample in 1..8192 && sample and (sample - 1) == 0)
        val w = (info.width + sample - 1) / sample
        val h = (info.height + sample - 1) / sample
        require(w.toLong() * h <= 4_194_304L)
        val pixels = IntArray(w * h)
        val colors = IntArray(4)
        val alpha = IntArray(8)
        var p = 128
        var blocks = 0
        for (by in 0 until (info.height + 3) / 4) {
            for (bx in 0 until (info.width + 3) / 4) {
                if (blocks++ % 1024 == 0) checkpoint()
                val cp = p + if (info.format == Format.DXT1) 0 else 8
                val c0 = word(bytes, cp)
                val c1 = word(bytes, cp + 2)
                colors[0] = rgb565(c0)
                colors[1] = rgb565(c1)
                if (info.format != Format.DXT1 || c0 > c1) {
                    colors[2] = blend(colors[0], colors[1], 2, 1)
                    colors[3] = blend(colors[0], colors[1], 1, 2)
                } else {
                    colors[2] = blend(colors[0], colors[1], 1, 1)
                    colors[3] = 0
                }
                val indices = dword(bytes, cp + 4)
                var alphaBits = 0L
                if (info.format == Format.DXT5) {
                    alpha[0] = u(bytes, p)
                    alpha[1] = u(bytes, p + 1)
                    val steps = if (alpha[0] > alpha[1]) 7 else 5
                    for (i in 1 until steps) alpha[i + 1] = ((steps - i) * alpha[0] + i * alpha[1]) / steps
                    if (steps == 5) { alpha[6] = 0; alpha[7] = 255 }
                    for (i in 0..5) alphaBits = alphaBits or (u(bytes, p + 2 + i).toLong() shl (8 * i))
                }
                for (i in 0..15) {
                    val x = bx * 4 + i % 4
                    val y = by * 4 + i / 4
                    if (x >= info.width || y >= info.height || x % sample != 0 || y % sample != 0) continue
                    val color = colors[indices ushr (2 * i) and 3]
                    val a = when (info.format) {
                        Format.DXT1 -> color ushr 24
                        Format.DXT3 -> (u(bytes, p + i / 2) ushr (4 * (i % 2)) and 15) * 17
                        Format.DXT5 -> alpha[(alphaBits ushr (3 * i) and 7).toInt()]
                    }
                    pixels[y / sample * w + x / sample] = (color and 0xffffff) or (a shl 24)
                }
                p += info.format.blockBytes
            }
        }
        checkpoint()
        return Pixels(w, h, pixels)
    }
}
