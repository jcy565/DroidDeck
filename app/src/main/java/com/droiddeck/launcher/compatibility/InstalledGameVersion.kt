package com.droiddeck.launcher.compatibility

/** Locally observed installation metadata, never a catalog's latest available version. */
internal object InstalledGameVersion {
    private val field = Regex(""""([^"\\]*)"\s+"([^"\\]*)"""")

    fun storeVersion(raw: String?): String? = raw
        ?.takeIf { it.length <= 128 && it.none(Char::isISOControl) }
        ?.trim()?.takeIf { it.isNotEmpty() }

    /** Accept only the canonical, quoted, line-oriented AppState manifest shape.
     * Unknown or ambiguous data is deliberately left unversioned. */
    fun steamBuildId(manifest: String, expectedAppId: Int): String? {
        val lines = manifest.lineSequence().map { it.trim().removePrefix("\uFEFF") }
            .filter { it.isNotEmpty() && !it.startsWith("//") }.iterator()
        if (!lines.hasNext() || lines.next() != "\"AppState\"") return null
        if (!lines.hasNext() || lines.next() != "{") return null
        var depth = 1
        var appId: String? = null
        var buildId: String? = null
        while (lines.hasNext()) {
            val line = lines.next()
            when (line) {
                "{" -> depth++
                "}" -> {
                    depth--
                    if (depth == 0) {
                        if (lines.hasNext() || appId != expectedAppId.toString()) return null
                        return buildId?.takeIf { it.length in 1..20 && it.all { c -> c in '0'..'9' } && it.any { c -> c != '0' } }
                    }
                }
                else -> if (depth == 1) {
                    val match = field.matchEntire(line) ?: continue
                    when (match.groupValues[1]) {
                        "appid" -> { if (appId != null) return null; appId = match.groupValues[2] }
                        "buildid" -> { if (buildId != null) return null; buildId = match.groupValues[2] }
                    }
                }
            }
        }
        return null
    }
}
