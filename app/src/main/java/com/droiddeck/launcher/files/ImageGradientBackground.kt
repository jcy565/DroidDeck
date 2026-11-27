package com.droiddeck.launcher.files

/** A conservative two-colour gradient estimate, not general image inpainting. */
internal object ImageGradientBackground {
    enum class Axis { HORIZONTAL, VERTICAL }
    data class Gradient(val axis: Axis, val start: Int, val end: Int)

    private fun rgb(r: Int, g: Int, b: Int): Int =
        0xff000000.toInt() or (r shl 16) or (g shl 8) or b
    private fun channel(color: Int, shift: Int): Int = (color ushr shift) and 255

    fun mix(a: Int, b: Int, step: Int): Int {
        require(step in 0..4)
        fun c(shift: Int) = (channel(a, shift) * (4 - step) + channel(b, shift) * step + 2) / 4
        return rgb(c(16), c(8), c(0))
    }

    private fun average(colors: List<Int>): Int {
        fun c(shift: Int) = colors.sumOf { channel(it, shift) } / colors.size
        return rgb(c(16), c(8), c(0))
    }

    private fun error(a: Int, b: Int): Int =
        maxOf(kotlin.math.abs(channel(a, 16) - channel(b, 16)),
            kotlin.math.abs(channel(a, 8) - channel(b, 8)),
            kotlin.math.abs(channel(a, 0) - channel(b, 0)))

    /** Five samples per edge, from left to right or top to bottom. Reject alpha and irregular edges. */
    fun estimate(top: List<Int>, bottom: List<Int>, left: List<Int>, right: List<Int>): Gradient? {
        val edges = listOf(top, bottom, left, right)
        if (edges.any { it.size != 5 || it.any { color -> color ushr 24 != 255 } }) return null
        val horizontal = Gradient(Axis.HORIZONTAL, average(left), average(right))
        val vertical = Gradient(Axis.VERTICAL, average(top), average(bottom))
        fun score(g: Gradient): Int {
            var worst = 0
            for (i in 0..4) {
                val mixed = mix(g.start, g.end, i)
                val predicted = if (g.axis == Axis.HORIZONTAL)
                    listOf(mixed, mixed, g.start, g.end) else listOf(g.start, g.end, mixed, mixed)
                for (j in edges.indices) worst = maxOf(worst, error(edges[j][i], predicted[j]))
            }
            return worst
        }
        val h = score(horizontal)
        val v = score(vertical)
        return when {
            h <= 24 && h <= v -> horizontal
            v <= 24 -> vertical
            else -> null
        }
    }
}
