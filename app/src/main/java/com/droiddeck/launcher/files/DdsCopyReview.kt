package com.droiddeck.launcher.files

/** Verify that a candidate preserves the source header and every non-edited BC block. */
internal object DdsCopyReview {
    data class Stats(val totalBlocks: Int, val editableBlocks: Int, val changedBlocks: Int)

    fun validate(source: ByteArray, candidate: ByteArray, regions: List<DdsCopyEncoder.Rect>,
        checkpoint: () -> Unit = {}): Stats {
        val info = DdsImage.inspect(source)
        require(DdsImage.inspect(candidate) == info && candidate.size == source.size)
        require((0 until 128).all { source[it] == candidate[it] })
        require(regions.size in 1..100 && regions.all { it.left >= 0 && it.top >= 0 &&
            it.left < it.right && it.top < it.bottom && it.right <= info.width && it.bottom <= info.height })
        var pos = 128
        var total = 0
        var editable = 0
        var changed = 0
        for (y in 0 until info.height step 4) for (x in 0 until info.width step 4) {
            if (total % 256 == 0) checkpoint()
            total++
            val allowed = regions.any { x < it.right && x + 4 > it.left && y < it.bottom && y + 4 > it.top }
            if (allowed) editable++
            if ((0 until info.format.blockBytes).any { source[pos + it] != candidate[pos + it] }) {
                require(allowed) { "번역 영역 외 DDS 블록이 변경됐습니다." }
                changed++
            }
            pos += info.format.blockBytes
        }
        checkpoint()
        return Stats(total, editable, changed)
    }
}
