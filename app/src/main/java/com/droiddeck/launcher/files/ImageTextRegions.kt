package com.droiddeck.launcher.files

/** Pixel rectangles in the decoded, unrotated OCR frame, not the original full-resolution asset. */
internal object ImageTextRegions {
    const val MAX_REGIONS = 100
    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)
    data class Region(val id: Int, val box: Box, val text: String)
    data class Result(val text: String, val width: Int, val height: Int,
        val regions: List<Region>, val partial: Boolean)

    fun validBox(box: Box, width: Int, height: Int): Boolean =
        width > 0 && height > 0 && box.left >= 0 && box.top >= 0 &&
            box.left < box.right && box.top < box.bottom &&
            box.right <= width && box.bottom <= height

    fun build(text: String, width: Int, height: Int, lines: Sequence<Pair<Box?, String>>): Result {
        ImageTranslationInput.validateText(text)
        require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 4_194_304L)
        val regions = mutableListOf<Region>()
        var partial = false
        var total = 0
        for ((box, line) in lines) {
            if (regions.size >= MAX_REGIONS) {
                partial = true
                break
            }
            if (box == null || !validBox(box, width, height) ||
                line.isBlank() || line.length > 1000 || line.any(Char::isISOControl) ||
                total + line.length > ImageTranslationInput.MAX_TEXT) {
                partial = true
                continue
            }
            regions.add(Region(regions.size + 1, box, line))
            total += line.length
        }
        return Result(text, width, height, regions.toList(), partial)
    }

    /** Produce a separate reviewed draft, never an instruction to modify the image. */
    fun summary(result: Result, edits: Map<Int, String>): String {
        require(edits.keys.all { id -> result.regions.any { it.id == id } })
        val translated = result.regions.mapNotNull { region ->
            edits[region.id]?.let {
                require(it.length <= 1000 && it.none(Char::isISOControl))
                ImageTranslationInput.validateText(it)
            }
        }
        return ImageTranslationInput.validateText(translated.joinToString("\n"))
    }
}
