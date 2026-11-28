package com.droiddeck.launcher.files

import java.util.zip.Inflater

/** Verifies zlib framing and filtered scanline lengths without allocating decoded pixels. */
internal class ImagePatchPngStream(width: Int, height: Int, depth: Int, color: Int, interlaced: Boolean) : java.io.Closeable {
    private val inflater: Inflater
    private val buffer = ByteArray(8192)
    private val rowLengths: List<Int>
    private var row = 0
    private var position = 0

    init {
        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 8_388_608L)
        val channels = when (color) { 0, 3 -> 1; 2 -> 3; 4 -> 2; 6 -> 4; else -> error("Invalid PNG color") }
        require(when (color) { 0 -> depth in setOf(1, 2, 4, 8, 16); 3 -> depth in setOf(1, 2, 4, 8); else -> depth in setOf(8, 16) })
        val rows = mutableListOf<Int>()
        fun pass(x: Int, y: Int, dx: Int, dy: Int) {
            if (width <= x || height <= y) return
            val w = (width - x + dx - 1) / dx
            val h = (height - y + dy - 1) / dy
            val length = 1 + (w * channels * depth + 7) / 8
            repeat(h) { rows.add(length) }
        }
        if (interlaced) {
            pass(0, 0, 8, 8); pass(4, 0, 8, 8); pass(0, 4, 4, 8)
            pass(2, 0, 4, 4); pass(0, 2, 2, 4); pass(1, 0, 2, 2); pass(0, 1, 1, 2)
        } else pass(0, 0, 1, 1)
        rowLengths = rows
        inflater = Inflater()
    }

    fun feed(bytes: ByteArray, offset: Int, length: Int, checkpoint: () -> Unit) {
        checkpoint()
        require(offset >= 0 && length >= 0 && offset <= bytes.size - length)
        if (length == 0) return
        require(!inflater.finished() && inflater.needsInput())
        inflater.setInput(bytes, offset, length)
        while (true) {
            checkpoint()
            val count = inflater.inflate(buffer)
            var index = 0
            while (index < count) {
                require(row < rowLengths.size) { "Excess PNG scanline data" }
                if (position == 0) require(buffer[index].toInt() in 0..4) { "Invalid PNG filter" }
                val take = minOf(count - index, rowLengths[row] - position)
                index += take
                position += take
                if (position == rowLengths[row]) { row++; position = 0 }
            }
            require(!inflater.needsDictionary())
            if (inflater.finished()) {
                require(inflater.remaining == 0) { "Trailing compressed PNG data" }
                break
            }
            if (inflater.needsInput() && count < buffer.size) break
            require(count > 0) { "PNG inflater made no progress" }
        }
    }

    fun finish() {
        require(inflater.finished() && row == rowLengths.size && position == 0) { "Incomplete PNG scanline data" }
    }

    override fun close() = inflater.end()
}
