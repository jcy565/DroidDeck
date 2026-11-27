package com.droiddeck.launcher.files

/** Select only missing draft entries, wrapping once without repeating a row. */
internal object TranslationAgentWindow {
    fun missing(ids: List<String>, edited: Set<String>, start: Int, limit: Int): List<Int> {
        require(ids.isNotEmpty() && ids.size == ids.toSet().size)
        require(start in 0..ids.size && limit > 0)
        require(edited.all { it in ids })
        return (0 until ids.size).asSequence()
            .map { (start + it) % ids.size }
            .filter { ids[it] !in edited }
            .take(limit)
            .toList()
    }

    fun next(ids: List<String>, edited: Set<String>, start: Int): Int =
        missing(ids, edited, start, 1).firstOrNull() ?: ids.size
}
