package com.droiddeck.launcher.files

/** An opaque caption over an OCR rectangle, not background reconstruction. */
internal object ImageOverlayPlan {
    data class Item(val box: ImageTextRegions.Box, val text: String)

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
