package com.droiddeck.launcher.compatibility

import com.droiddeck.launcher.session.SessionDisplay

/** Landscape reference dimensions derived from the existing resolver, not telemetry. */
internal object OutputResolutionComparison {
    data class Comparison(val current: Pair<Int, Int>, val proposed: Pair<Int, Int>,
        val currentFollowsWindow: Boolean) {
        val sameDimensions: Boolean get() = current == proposed
        val description: String
            get() = "${current.first}×${current.second} → ${proposed.first}×${proposed.second}"
    }

    fun calculate(panel: Pair<Int, Int>, before: String, proposed: String): Comparison? {
        if (panel.first <= 0 || panel.second <= 0) return null
        if (proposed !in setOf("1280x720", "1600x900")) return null
        if (before != SessionDisplay.MATCH_SCREEN && !SessionDisplay.isFollow(before) &&
            SessionDisplay.presetHeight(before) == null &&
            !before.matches(Regex("[1-9][0-9]*x[1-9][0-9]*"))) return null
        // Do not treat malformed explicit dimensions as the resolver's default fallback.
        if ('x' in before && before != SessionDisplay.MATCH_SCREEN &&
            before.split('x').any { it.toIntOrNull()?.let { n -> n > 0 } != true }) return null
        val current = SessionDisplay.resolveChoice(panel, before)
        val target = SessionDisplay.resolveChoice(panel, proposed)
        if (listOf(current.first, current.second, target.first, target.second).any { it <= 0 }) return null
        return Comparison(current, target, SessionDisplay.isFollow(before))
    }
}
