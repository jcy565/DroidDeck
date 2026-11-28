package com.droiddeck.launcher.files

internal object GameConfigPreview {
    data class Change(val key: String, val before: String, val after: String)
    data class Preview(val original: String, val proposed: String, val changes: List<Change>)

    private val sectionHeader = Regex("""\[([^\[\]]+)](?:\s*[;#].*)?""")

    fun sectionName(line: String): String? {
        val text = line.trim()
        if (!text.startsWith('[')) return null
        val name = sectionHeader.matchEntire(text)?.groupValues?.get(1)?.trim()
        require(!name.isNullOrEmpty()) { "Ambiguous section header" }
        return name
    }

    fun preview(original: String, section: String?, updates: Map<String, String>): Preview {
        require(original.length <= 262144)
        TranslationJson.validUnicode(original)
        section?.let(TranslationJson::validUnicode)
        val stable = updates.toMap()
        require(stable.isNotEmpty())
        require(stable.all { (k, v) -> k.matches(Regex("[A-Za-z0-9_. -]+")) && v.none { it.isISOControl() } })
        stable.values.forEach(TranslationJson::validUnicode)
        val seen = mutableSetOf<String>()
        val changes = mutableListOf<Change>()
        var current: String? = null
        val proposed = Regex("[^\\r\\n]+|\\r\\n|\\r|\\n").replace(original) { match ->
            val line = match.value
            val content = if (match.range.first == 0) line.removePrefix("\uFEFF") else line
            sectionName(content)?.let { current = it }
            val eq = line.indexOf('=')
            val key = if (eq > 0) content.substringBefore('=').trim() else ""
            val value = stable[key]
            if (current != section || value == null) line
            else {
                require(seen.add(key)) { "Duplicate key: $key" }
                val before = line.substring(eq + 1)
                require(!before.contains(';') && !before.contains('#')) { "Ambiguous inline comment" }
                if (before.trim() == value) line else {
                    changes.add(Change(key, before.trim(), value))
                    val leading = before.takeWhile { it.isWhitespace() }
                    val trailing = before.drop(leading.length).takeLastWhile { it.isWhitespace() }
                    line.substring(0, eq + 1) + leading + value + trailing
                }
            }
        }
        require(seen.containsAll(stable.keys)) { "Setting not found" }
        return Preview(original, proposed, changes.toList())
    }
}
