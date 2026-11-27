package com.droiddeck.launcher.files

/** Reuse exact source sentences only, within the currently selected file. */
internal object TranslationReuse {
    enum class Status { MISSING, UNCHANGED, INVALID, READY }
    data class Review(val statuses: Map<String, Status>) {
        fun count(status: Status): Int = statuses.values.count { it == status }
        fun nextUnfinished(ids: List<String>, current: Int): Int? {
            require(current in ids.indices && ids.toSet() == statuses.keys)
            return (1..ids.size).map { (current + it) % ids.size }
                .firstOrNull { statuses[ids[it]] != Status.READY }
        }
    }
    data class Result(val edits: Map<String, String>, val added: Int, val conflicts: Int)

    private fun status(row: GamePatchAuthor.Row, value: String?): Status = when {
        value == null -> Status.MISSING
        value == row.text -> Status.UNCHANGED
        !GamePatchAuthor.validTranslation(row.text, value) -> Status.INVALID
        else -> Status.READY
    }

    fun review(source: String, edits: Map<String, String>): Review {
        val rows = GamePatchAuthor.extract(source)
        require(edits.keys.all { id -> rows.any { it.id == id } })
        return Review(rows.associate { it.id to status(it, edits[it.id]) })
    }

    fun fill(source: String, edits: Map<String, String>): Result {
        val rows = GamePatchAuthor.extract(source)
        require(edits.keys.all { id -> rows.any { it.id == id } })
        val result = edits.toMutableMap()
        var added = 0
        var conflicts = 0
        for (group in rows.groupBy { it.text }.values) {
            val missing = group.filter { it.id !in edits }
            if (missing.isEmpty()) continue
            val supplied = group.mapNotNull { edits[it.id] }.distinct()
            if (supplied.isEmpty()) continue
            if (supplied.size != 1 || status(group.first(), supplied.single()) != Status.READY) {
                conflicts += missing.size
                continue
            }
            for (row in missing) {
                result[row.id] = supplied.single()
                added++
            }
        }
        return Result(result.toMap(), added, conflicts)
    }
}
