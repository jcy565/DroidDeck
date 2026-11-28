package com.droiddeck.launcher.files

import java.util.zip.CRC32

/** Bounded static PNG container and filtered scanline validation. Not full pixel decoding. */
internal object ImagePatchPngCheck {
    private val signature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    private const val MAX_CHUNKS = 16384

    private fun u32(bytes: ByteArray, p: Int): Long =
        (0..3).fold(0L) { value, i -> (value shl 8) or (bytes[p + i].toLong() and 255L) }

    fun validate(bytes: ByteArray, width: Int, height: Int, checkpoint: () -> Unit = {}) {
        require(bytes.size in 45..8 * 1024 * 1024)
        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 8_388_608L)
        require(signature.indices.all { bytes[it] == signature[it] })
        var offset = signature.size
        var chunks = 0
        var color = -1
        var depth = -1
        var paletteEntries = 0
        var transparency = false
        var data = false
        var dataEnded = false
        var dataBytes = 0L
        var stream: ImagePatchPngStream? = null
        try {
            while (offset < bytes.size) {
                checkpoint()
                require(++chunks <= MAX_CHUNKS && bytes.size - offset >= 12)
                val length = u32(bytes, offset)
                require(length <= bytes.size - offset - 12L)
                val size = length.toInt()
                val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
                require(type.all { it in 'A'..'Z' || it in 'a'..'z' } && type[2] in 'A'..'Z')
                val payload = offset + 8
                val crc = CRC32()
                var crcOffset = offset + 4
                val crcEnd = payload + size
                while (crcOffset < crcEnd) {
                    checkpoint()
                    val count = minOf(65536, crcEnd - crcOffset)
                    crc.update(bytes, crcOffset, count)
                    crcOffset += count
                }
                checkpoint()
                require(crc.value == u32(bytes, payload + size))
                require(chunks != 1 || type == "IHDR")
                if (data && type != "IDAT") dataEnded = true
                when (type) {
                    "IHDR" -> {
                        require(chunks == 1 && size == 13)
                        require(u32(bytes, payload) == width.toLong() && u32(bytes, payload + 4) == height.toLong())
                        depth = bytes[payload + 8].toInt() and 255
                        color = bytes[payload + 9].toInt() and 255
                        require(when (color) {
                            0 -> depth in setOf(1, 2, 4, 8, 16)
                            2, 4, 6 -> depth in setOf(8, 16)
                            3 -> depth in setOf(1, 2, 4, 8)
                            else -> false
                        })
                        require(bytes[payload + 10].toInt() == 0 && bytes[payload + 11].toInt() == 0 &&
                            bytes[payload + 12].toInt() in 0..1)
                        stream = ImagePatchPngStream(width, height, depth, color, bytes[payload + 12].toInt() == 1)
                    }
                    "PLTE" -> {
                        require(paletteEntries == 0 && !transparency && !data && color !in setOf(0, 4) && size in 3..768 && size % 3 == 0)
                        if (color == 3) require(size / 3 <= (1 shl depth))
                        paletteEntries = size / 3
                    }
                    "tRNS" -> {
                        require(!transparency && !data)
                        fun sample(at: Int) = ((bytes[at].toInt() and 255) shl 8) or (bytes[at + 1].toInt() and 255)
                        val maximum = (1 shl depth) - 1
                        when (color) {
                            0 -> require(size == 2 && sample(payload) <= maximum)
                            2 -> require(size == 6 && (0..2).all { sample(payload + it * 2) <= maximum })
                            3 -> require(size in 1..paletteEntries)
                            else -> error("PNG with alpha must not contain tRNS")
                        }
                        transparency = true
                    }
                    "IDAT" -> {
                        require(!dataEnded && (color != 3 || paletteEntries > 0))
                        data = true
                        dataBytes += size
                        requireNotNull(stream).feed(bytes, payload, size, checkpoint)
                    }
                    "IEND" -> {
                        require(size == 0 && data && dataBytes > 0 && payload + 4 == bytes.size)
                        requireNotNull(stream).finish()
                        checkpoint()
                        return
                    }
                    "acTL", "fcTL", "fdAT" -> error("Animated PNG is not supported")
                    else -> require(type[0] in 'a'..'z') { "Unknown critical PNG chunk" }
                }
                offset = payload + size + 4
            }
            error("PNG has no IEND")
        } finally { stream?.close() }
    }
}
