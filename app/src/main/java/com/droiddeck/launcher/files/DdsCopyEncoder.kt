package com.droiddeck.launcher.files

import java.io.OutputStream

/** Low-complexity BC encoder. Preserve source bytes for blocks outside reviewed rectangles. */
internal object DdsCopyEncoder {
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun channel(c: Int, shift: Int) = c ushr shift and 255
    private fun packed(c: Int): Int = ((channel(c, 16) * 31 + 127) / 255 shl 11) or
        ((channel(c, 8) * 63 + 127) / 255 shl 5) or ((channel(c, 0) * 31 + 127) / 255)
    private fun rgb(v: Int): Int {
        val r = v ushr 11 and 31
        val g = v ushr 5 and 63
        val b = v and 31
        return ((r shl 3 or (r ushr 2)) shl 16) or ((g shl 2 or (g ushr 4)) shl 8) or (b shl 3 or (b ushr 2))
    }
    private fun mix(a: Int, b: Int, wa: Int, wb: Int): Int {
        fun c(s: Int) = ((channel(a, s) * wa + channel(b, s) * wb) / (wa + wb)) shl s
        return c(16) or c(8) or c(0)
    }
    private fun distance(a: Int, b: Int): Int {
        fun d(s: Int): Int { val n = channel(a, s) - channel(b, s); return n * n }
        return d(16) + d(8) + d(0)
    }
    private fun put(out: ByteArray, pos: Int, value: Long, count: Int) {
        for (i in 0 until count) out[pos + i] = (value ushr (8 * i)).toByte()
    }

    fun encodeBlock(format: DdsImage.Format, pixels: IntArray): ByteArray {
        require(pixels.size == 16)
        val bc1 = format == DdsImage.Format.DXT1
        if (bc1) require(pixels.all { channel(it, 24) in setOf(0, 255) }) { "DXT1은 중간 투명도를 저장할 수 없습니다." }
        val transparent = bc1 && pixels.any { channel(it, 24) == 0 }
        var lo = 0
        var hi = 0
        var min = Int.MAX_VALUE
        var max = -1
        for (c in pixels) {
            if (transparent && channel(c, 24) == 0) continue
            val light = 3 * channel(c, 16) + 6 * channel(c, 8) + channel(c, 0)
            if (light < min) { min = light; lo = packed(c) }
            if (light > max) { max = light; hi = packed(c) }
        }
        var c0 = maxOf(lo, hi)
        var c1 = minOf(lo, hi)
        if (transparent) { val t = c0; c0 = c1; c1 = t }
        else if (bc1 && c0 == c1) { if (c0 < 65535) c0++ else c1-- }
        val a = rgb(c0)
        val b = rgb(c1)
        val palette = if (transparent) intArrayOf(a, b, mix(a, b, 1, 1))
            else intArrayOf(a, b, mix(a, b, 2, 1), mix(a, b, 1, 2))
        var indices = 0L
        for (i in pixels.indices) {
            val c = pixels[i]
            var best = 0
            if (transparent && channel(c, 24) == 0) best = 3
            else for (j in 1 until palette.size) if (distance(c, palette[j]) < distance(c, palette[best])) best = j
            indices = indices or (best.toLong() shl (2 * i))
        }
        val out = ByteArray(format.blockBytes)
        val cp = if (bc1) 0 else 8
        put(out, cp, c0.toLong(), 2); put(out, cp + 2, c1.toLong(), 2); put(out, cp + 4, indices, 4)
        if (format == DdsImage.Format.DXT3) {
            var bits = 0L
            for (i in pixels.indices) bits = bits or (((channel(pixels[i], 24) * 15 + 127) / 255).toLong() shl (4 * i))
            put(out, 0, bits, 8)
        } else if (format == DdsImage.Format.DXT5) {
            val top = pixels.maxOf { channel(it, 24) }
            val bottom = pixels.minOf { channel(it, 24) }
            out[0] = top.toByte(); out[1] = bottom.toByte()
            val alpha = IntArray(8)
            alpha[0] = top; alpha[1] = bottom
            if (top > bottom) for (i in 1..6) alpha[i + 1] = ((7 - i) * top + i * bottom) / 7
            else { for (i in 2..5) alpha[i] = top; alpha[6] = 0; alpha[7] = 255 }
            var bits = 0L
            for (i in pixels.indices) {
                val value = channel(pixels[i], 24)
                var best = 0
                for (j in 1..7) if (kotlin.math.abs(value - alpha[j]) < kotlin.math.abs(value - alpha[best])) best = j
                bits = bits or (best.toLong() shl (3 * i))
            }
            put(out, 2, bits, 6)
        }
        return out
    }

    fun writeCopy(source: ByteArray, width: Int, height: Int, regions: List<Rect>, output: OutputStream,
        readBlock: (Int, Int, IntArray) -> Unit, checkpoint: () -> Unit = {}) {
        val info = DdsImage.inspect(source)
        require(width == info.width && height == info.height)
        require(regions.size in 1..100 && regions.all { it.left >= 0 && it.top >= 0 &&
            it.right in (it.left + 1)..width && it.bottom in (it.top + 1)..height })
        checkpoint()
        output.write(source, 0, 128)
        val pixels = IntArray(16)
        var pos = 128
        var blocks = 0
        for (y in 0 until height step 4) for (x in 0 until width step 4) {
            if (blocks++ % 256 == 0) checkpoint()
            if (regions.any { x < it.right && x + 4 > it.left && y < it.bottom && y + 4 > it.top }) {
                readBlock(x, y, pixels)
                output.write(encodeBlock(info.format, pixels))
            } else output.write(source, pos, info.format.blockBytes)
            pos += info.format.blockBytes
        }
        checkpoint()
    }
}
