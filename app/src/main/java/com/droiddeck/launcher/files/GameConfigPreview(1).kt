package com.droiddeck.launcher.files

internal object GameConfigPreview {
    data class Change(val key: String, val before: String, val after: String)
    data class Preview(val original: String, val proposed: String, val changes: List<Change>)

    fun preview(original: String, section: String?, updates: Map<String, String>): Preview {
        require(original.length <= 262144)
        require(updates.isNotEmpty())
        require(updates.all { (k, v) -> k.matches(Regex("[A-Za-z0-9_. -]+")) && v.none { it.isISOControl() } })
        val seen = mutableSetOf<String>()
        val changes = mutableListOf<Change>()
        var current: String? = null
        val proposed = Regex("[^\\r\\n]+|\\r\\n|\\r|\\n").replace(original) { match ->
            val line = match.value
            val trimmed = line.trim()
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                current = trimmed.substring(1, trimmed.length - 1).trim()
            }
            val eq = line.indexOf('=')
            val key = if (eq > 0) line.substring(0, eq).trim() else ""
            val value = updates[key]
            if (current != section || value == null || trimmed.startsWith("#") || trimmed.startsWith(";")) line
            else {
                require(seen.add(key)) { "Duplicate key: $key" }
                val before = line.substring(eq + 1)
                require(!before.contains(';') && !before.contains('#')) { "Ambiguous inline comment" }
                if (before.trim() == value) line else {
                    changes.add(Change(key, before.trim(), value))
                    line.substring(0, eq + 1) + value
                }
            }
        }
        require(seen.containsAll(updates.keys)) { "Setting not found" }
        return Preview(original, proposed, changes.toList())
    }
}
