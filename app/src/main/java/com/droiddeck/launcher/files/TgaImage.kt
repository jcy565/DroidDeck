package com.droiddeck.launcher.files

/** Strict subset of TGA: uncompressed true-colour, 24-bit RGB or 32-bit BGRA. */
internal object TgaImage {
    data class Info(val width: Int, val height: Int, val depth: Int,
        val topOrigin: Boolean, val rightOrigin: Boolean, val offset: Int)
    data class Pixels(val width: Int, val height: Int, val argb: IntArray)

    fun inspect(bytes: ByteArray): Info {
        require(bytes.size in 18..ImageTranslationInput.MAX_BYTES)
        fun u(i: Int) = bytes[i].toInt() and 255
        fun word(i: Int) = u(i) or (u(i + 1) shl 8)
        require(u(1) == 0 && u(2) == 2 && (3..7).all { u(it) == 0 }) {
            "비압축 트루컬러 TGA만 지원합니다."
        }
        val width = word(12)
        val height = word(14)
        ImageTranslationInput.sampleSize(width, height)
        val depth = u(16)
        val descriptor = u(17)
        require(descriptor and 0xc0 == 0)
        require((depth == 24 && descriptor and 15 == 0) ||
            (depth == 32 && descriptor and 15 == 8)) { "24비트 RGB 또는 32비트 BGRA TGA만 지원합니다." }
        val offset = 18 + u(0)
        val end = offset.toLong() + width.toLong() * height * (depth / 8)
        require(end <= bytes.size)
        // Optional TGA 2.0 footer, but no developer/extension areas with unknown alpha semantics.
        if (end != bytes.size.toLong()) {
            require(end + 26 == bytes.size.toLong())
            val footer = end.toInt()
            require((footer until footer + 8).all { u(it) == 0 })
            val signature = "TRUEVISION-XFILE.\u0000".toByteArray(Charsets.US_ASCII)
            require(signature.indices.all { bytes[footer + 8 + it] == signature[it] })
        }
        return Info(width, height, depth, descriptor and 32 != 0, descriptor and 16 != 0, offset)
    }

    fun decode(bytes: ByteArray, sample: Int, checkpoint: () -> Unit = {}): Pixels {
        val info = inspect(bytes)
        require(sample in 1..8192 && sample and (sample - 1) == 0)
        val w = (info.width + sample - 1) / sample
        val h = (info.height + sample - 1) / sample
        require(w.toLong() * h <= 4_194_304L)
        val pixels = IntArray(w * h)
        val stride = info.depth / 8
        fun u(i: Int) = bytes[i].toInt() and 255
        for (y in 0 until h) {
            if (y % 32 == 0) checkpoint()
            val sy = if (info.topOrigin) y * sample else info.height - 1 - y * sample
            for (x in 0 until w) {
                val sx = if (info.rightOrigin) info.width - 1 - x * sample else x * sample
                val p = info.offset + (sy * info.width + sx) * stride
                val alpha = if (stride == 4) u(p + 3) else 255
                pixels[y * w + x] = (alpha shl 24) or (u(p + 2) shl 16) or (u(p + 1) shl 8) or u(p)
            }
        }
        checkpoint()
        return Pixels(w, h, pixels)
    }
}
