package com.droiddeck.launcher.files

import java.io.File

/** Explicit per-file reuse of saved, structurally valid translations in the same worklist. */
internal object TranslationWorklistReuse {
    private const val MAX_SUGGESTIONS = 25
    data class Suggestion(val id: String, val original: String, val translated: String)
    data class Plan(val target: TranslationDiscovery.Candidate, val expected: Map<String, String>?,
        val suggestions: List<Suggestion>, val conflicts: Int)

    fun prepare(queue: TranslationWorklist.Queue, target: TranslationDiscovery.Candidate,
        drafts: File, checkpoint: () -> Unit = {}): Plan {
        val stable = queue.copy(files = queue.files.toList())
        require(target in stable.files)
        checkpoint()
        val source = TranslationDiscovery.snapshot(stable.root, target)
        val existing = TranslationDraftStore.load(drafts, source.file.canonicalPath, source.source)
        val rows = GamePatchAuthor.extract(source.source).filter { it.id !in existing.orEmpty() }
        val wanted = rows.map { it.text }.toSet()
        val matches = mutableMapOf<String, String>()
        val conflicts = mutableSetOf<String>()
        for (candidate in stable.files) {
            if (candidate == target) continue
            checkpoint()
            val donor = TranslationDiscovery.snapshot(stable.root, candidate)
            val edits = TranslationDraftStore.load(drafts, donor.file.canonicalPath, donor.source) ?: continue
            for (row in GamePatchAuthor.extract(donor.source)) {
                if (row.text !in wanted) continue
                val value = edits[row.id] ?: continue
                if (value == row.text || !GamePatchAuthor.validTranslation(row.text, value)) {
                    conflicts.add(row.text)
                    continue
                }
                val previous = matches.putIfAbsent(row.text, value)
                if (previous != null && previous != value) conflicts.add(row.text)
            }
        }
        // A conflicting translation already entered in the destination also blocks reuse.
        for (row in GamePatchAuthor.extract(source.source)) {
            val entered = existing?.get(row.id) ?: continue
            val proposed = matches[row.text] ?: continue
            if (entered != proposed) conflicts.add(row.text)
        }
        val suggestions = rows.asSequence().filter { it.text !in conflicts }
            .mapNotNull { row -> matches[row.text]?.let { Suggestion(row.id, row.text, it) } }
            .take(MAX_SUGGESTIONS).toList()
        checkpoint()
        return Plan(target, existing?.toMap(), suggestions, rows.count { it.text in conflicts })
    }

    fun saveReviewed(queue: TranslationWorklist.Queue, plan: Plan, drafts: File,
        checkpoint: () -> Unit = {}) {
        require(plan.suggestions.isNotEmpty())
        require(prepare(queue, plan.target, drafts, checkpoint) == plan) {
            "검토 이후 원본 또는 번역 초안이 변경됐습니다."
        }
        val source = TranslationDiscovery.snapshot(queue.root, plan.target)
        val edits = plan.expected.orEmpty() + plan.suggestions.associate { it.id to it.translated }
        checkpoint()
        TranslationDraftStore.saveIfUnchanged(drafts, source.file.canonicalPath, source.source,
            plan.expected, edits)
    }
}
