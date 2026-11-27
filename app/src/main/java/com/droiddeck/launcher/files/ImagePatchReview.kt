package com.droiddeck.launcher.files

/** Read-only assessment; never authorizes applying or restoring files. */
internal object ImagePatchReview {
    enum class Assessment { ORIGINAL_MATCH, APPLIED_MATCH, INTERRUPTED, OUTSIDE_JOURNAL, BLOCKED }

    fun assess(journal: ImagePatchTransaction.Journal, assets: List<ImagePatchTransaction.AssetCheck>): Assessment {
        val attempted = journal.attempted
        if (assets.isEmpty() || attempted !in 0..assets.size) return Assessment.BLOCKED
        val valid = when (journal.phase) {
            ImagePatchTransaction.Phase.PREPARED, ImagePatchTransaction.Phase.RESTORED -> attempted == 0
            ImagePatchTransaction.Phase.APPLYING -> attempted > 0
            ImagePatchTransaction.Phase.APPLIED -> attempted == assets.size
        }
        if (!valid || assets.any { it.state == ImagePatchTransaction.AssetState.CONFLICT ||
                it.state == ImagePatchTransaction.AssetState.UNAVAILABLE }) return Assessment.BLOCKED
        if (assets.drop(attempted).any { it.state != ImagePatchTransaction.AssetState.ORIGINAL })
            return Assessment.OUTSIDE_JOURNAL
        return when (journal.phase) {
            ImagePatchTransaction.Phase.PREPARED, ImagePatchTransaction.Phase.RESTORED -> Assessment.ORIGINAL_MATCH
            ImagePatchTransaction.Phase.APPLYING -> Assessment.INTERRUPTED
            ImagePatchTransaction.Phase.APPLIED -> if (assets.all { it.state == ImagePatchTransaction.AssetState.TRANSLATED })
                Assessment.APPLIED_MATCH else Assessment.INTERRUPTED
        }
    }
}
