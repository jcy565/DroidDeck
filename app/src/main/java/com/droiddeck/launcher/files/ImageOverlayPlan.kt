package com.droiddeck.launcher.files

/** An opaque caption over an OCR rectangle, not background reconstruction. */
internal object ImageOverlayPlan {
    data class Item(val box: ImageTextRegions.Box, val text: String)
    const val MAX_EXPORT_PIXELS = 8_388_608L
    enum class Background { MATCH_FLAT, BLACK }
    data class Palette(val background: Int, val foreground: Int)
    enum class Foreground(val label: String) { AUTO("자동"), BLACK("검정"), WHITE("흰색") }
    enum class Weight(val label: String) { NORMAL("보통"), BOLD("굵게") }
    data class Appearance(val background: Background, val foreground: Foreground, val weight: Weight)
    fun foreground(palette: Palette, choice: Foreground): Int = when (choice) {
        Foreground.AUTO -> palette.foreground
        Foreground.BLACK -> 0xff000000.toInt()
        Foreground.WHITE -> 0xffffffff.toInt()
    }

    fun comparisonSample(width: Int, height: Int): Int {
        var sample = ImageTranslationInput.sampleSize(width, height)
        while ((width.toLong() + sample - 1) / sample *
            ((height.toLong() + sample - 1) / sample) > 1_048_576L) sample *= 2
        return sample
    }

    /** Use shared integer boundaries so adjacent OCR boxes never overlap after scaling. */
    fun originalSize(items: List<Item>, frameWidth: Int, frameHeight: Int,
        width: Int, height: Int): List<Item> {
        require(frameWidth in 1..8192 && frameHeight in 1..8192 &&
            frameWidth.toLong() * frameHeight <= 4_194_304L)
        require(width in frameWidth..8192 && height in frameHeight..8192 &&
            width.toLong() * height <= MAX_EXPORT_PIXELS) { "원본 해상도 출력 한도를 초과했습니다." }
        return items.map { item ->
            val b = item.box
            require(ImageTextRegions.validBox(b, frameWidth, frameHeight))
            item.copy(box = ImageTextRegions.Box(
                (b.left.toLong() * width / frameWidth).toInt(),
                (b.top.toLong() * height / frameHeight).toInt(),
                (b.right.toLong() * width / frameWidth).toInt(),
                (b.bottom.toLong() * height / frameHeight).toInt()))
        }
    }

    /** Conservative flat-colour estimate; never claim textured or transparent background repair. */
    fun flatPalette(samples: List<Int>): Palette? {
        if (samples.size !in 8..64 || samples.any { it ushr 24 != 255 }) return null
        fun channel(shift: Int): Int? {
            val values = samples.map { (it ushr shift) and 255 }
            if (values.maxOrNull()!! - values.minOrNull()!! > 24) return null
            return values.sum() / values.size
        }
        val r = channel(16) ?: return null
        val g = channel(8) ?: return null
        val b = channel(0) ?: return null
        fun linear(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.04045) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        val luminance = 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
        val foreground = if (luminance >= 0.179) 0xff000000.toInt() else 0xffffffff.toInt()
        return Palette(0xff000000.toInt() or (r shl 16) or (g shl 8) or b, foreground)
    }

    private fun intersects(a: ImageTextRegions.Box, b: ImageTextRegions.Box): Boolean =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    fun prepare(result: ImageTextRegions.Result, edits: Map<Int, String>): List<Item> {
        require(result.width in 1..8192 && result.height in 1..8192 &&
            result.width.toLong() * result.height <= 4_194_304L)
        require(result.regions.size in 1..ImageTextRegions.MAX_REGIONS)
        require(result.regions.map { it.id }.distinct().size == result.regions.size)
        require(result.regions.all { ImageTextRegions.validBox(it.box, result.width, result.height) })
        require(edits.isNotEmpty() && edits.keys.all { id -> result.regions.any { it.id == id } })
        ImageTextRegions.summary(result, edits)
        return result.regions.mapNotNull { region ->
            val text = edits[region.id] ?: return@mapNotNull null
            require(result.regions.none { other -> other.id != region.id && intersects(region.box, other.box) }) {
                "다른 글자 영역과 겹칩니다. 해당 영역은 이미지로 출력할 수 없습니다."
            }
            require(region.box.right - region.box.left >= 12 && region.box.bottom - region.box.top >= 12) {
                "글자 영역이 너무 작습니다."
            }
            Item(region.box, text)
        }
    }
}
