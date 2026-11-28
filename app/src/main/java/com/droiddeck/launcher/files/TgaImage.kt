package com.droiddeck.launcher.files

import java.io.OutputStream

/** Bounded true-colour TGA: 24-bit RGB or 32-bit BGRA, raw or RLE input. */
internal object TgaImage {
    data class Info(val width: Int, val height: Int, val depth: Int,
        val topOrigin: Boolean, val rightOrigin: Boolean, val offset: Int, val rle: Boolean)
    data class Pixels(val width: Int, val height: Int, val argb: IntArray)

    private fun u(bytes: ByteArray, i: Int) = bytes[i].toInt() and 255

    /** Walk validated packet runs; never expand an entire compressed source in memory. */
    private fun walk(bytes: ByteArray, info: Info, checkpoint: () -> Unit,
        visit: (Int, Int, Int, Boolean) -> Unit): Int {
        val stride = info.depth / 8
        val total = info.width * info.height
        var position = info.offset
        var index = 0
        var packets = 0
        while (index < total) {
            if (packets++ % 1024 == 0) checkpoint()
            require(position < bytes.size)
            val header = u(bytes, position++)
            val count = (header and 127) + 1
            val repeat = header and 128 != 0
            require(count <= total - index)
            val length = if (repeat) stride else count * stride
            require(length <= bytes.size - position)
            visit(index, count, position, repeat)
            position += length
            index += count
        }
        checkpoint()
        return position
    }

    fun inspect(bytes: ByteArray, checkpoint: () -> Unit = {}): Info {
        checkpoint()
        require(bytes.size in 18..ImageTranslationInput.MAX_BYTES)
        fun u(i: Int) = u(bytes, i)
        fun word(i: Int) = u(i) or (u(i + 1) shl 8)
        require(u(1) == 0 && u(2) in setOf(2, 10) && (3..7).all { u(it) == 0 }) {
            "트루컬러 TGA만 지원합니다."
        }
        val width = word(12)
        val height = word(14)
        ImageTranslationInput.sampleSize(width, height)
        require(width.toLong() * height <= 8_388_608L)
        val depth = u(16)
        val descriptor = u(17)
        require(descriptor and 0xc0 == 0)
        require((depth == 24 && descriptor and 15 == 0) ||
            (depth == 32 && descriptor and 15 == 8)) { "24비트 RGB 또는 32비트 BGRA TGA만 지원합니다." }
        val offset = 18 + u(0)
        require(offset <= bytes.size)
        val info = Info(width, height, depth, descriptor and 32 != 0, descriptor and 16 != 0, offset, u(2) == 10)
        val end = if (info.rle) walk(bytes, info, checkpoint) { _, _, _, _ -> }.toLong()
            else offset.toLong() + width.toLong() * height * (depth / 8)
        require(end <= bytes.size)
        if (end != bytes.size.toLong()) {
            require(end + 26 == bytes.size.toLong())
            val footer = end.toInt()
            require((footer until footer + 8).all { u(it) == 0 })
            val signature = "TRUEVISION-XFILE.\u0000".toByteArray(Charsets.US_ASCII)
            require(signature.indices.all { bytes[footer + 8 + it] == signature[it] })
        }
        checkpoint()
        return info
    }

    fun decode(bytes: ByteArray, sample: Int, checkpoint: () -> Unit = {}): Pixels {
        val info = inspect(bytes, checkpoint)
        require(sample in 1..8192 && sample and (sample - 1) == 0)
        val w = (info.width + sample - 1) / sample
        val h = (info.height + sample - 1) / sample
        require(w.toLong() * h <= 4_194_304L)
        val pixels = IntArray(w * h)
        val stride = info.depth / 8
        fun color(p: Int): Int {
            val alpha = if (stride == 4) u(bytes, p + 3) else 255
            return (alpha shl 24) or (u(bytes, p + 2) shl 16) or (u(bytes, p + 1) shl 8) or u(bytes, p)
        }
        if (info.rle) {
            walk(bytes, info, checkpoint) { index, count, position, repeat ->
                for (i in 0 until count) {
                    val sy = (index + i) / info.width
                    val sx = (index + i) % info.width
                    val y = if (info.topOrigin) sy else info.height - 1 - sy
                    val x = if (info.rightOrigin) info.width - 1 - sx else sx
                    if (x % sample == 0 && y % sample == 0)
                        pixels[y / sample * w + x / sample] = color(position + if (repeat) 0 else i * stride)
                }
            }
        } else {
            for (y in 0 until h) {
                if (y % 32 == 0) checkpoint()
                val sy = if (info.topOrigin) y * sample else info.height - 1 - y * sample
                for (x in 0 until w) {
                    val sx = if (info.rightOrigin) info.width - 1 - x * sample else x * sample
                    pixels[y * w + x] = color(info.offset + (sy * info.width + sx) * stride)
                }
            }
        }
        checkpoint()
        return Pixels(w, h, pixels)
    }

    /** Stream a new uncompressed TGA with original dimensions, depth and origin flags. */
    fun writeCopy(source: ByteArray, width: Int, height: Int, output: OutputStream,
        readRow: (Int, IntArray) -> Unit, checkpoint: () -> Unit = {}) {
        val info = inspect(source, checkpoint)
        require(width == info.width && height == info.height)
        val stride = info.depth / 8
        require(18L + width.toLong() * height * stride <= ImageTranslationInput.MAX_BYTES) {
            "비압축 TGA 출력이 8MiB 한도를 초과합니다."
        }
        val header = source.copyOfRange(0, 18)
        header[0] = 0
        header[2] = 2
        checkpoint()
        output.write(header)
        val row = IntArray(width)
        val encoded = ByteArray(width * stride)
        for (sy in 0 until height) {
            if (sy % 16 == 0) checkpoint()
            val y = if (info.topOrigin) sy else height - 1 - sy
            readRow(y, row)
            checkpoint()
            for (sx in 0 until width) {
                val x = if (info.rightOrigin) width - 1 - sx else sx
                val c = row[x]
                require(stride == 4 || c ushr 24 == 255) { "24비트 TGA에는 투명도를 저장할 수 없습니다." }
                for (i in 0 until stride) encoded[sx * stride + i] = (c ushr (i * 8)).toByte()
            }
            checkpoint()
            output.write(encoded)
        }
        checkpoint()
    }
}
