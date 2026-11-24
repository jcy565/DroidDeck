package com.droiddeck.launcher.compatibility

import com.droiddeck.launcher.files.GamePatchStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object OptimizationRecovery {
    enum class Decision { REVIEW_RESTORE, NO_MATCHING_BACKUP, RETEST, KEEP_FOR_NOW }

    fun decide(verdict: OptimizationComparison.Verdict, matchingBackup: Boolean): Decision =
        when (verdict) {
            OptimizationComparison.Verdict.REGRESSED ->
                if (matchingBackup) Decision.REVIEW_RESTORE else Decision.NO_MATCHING_BACKUP
            OptimizationComparison.Verdict.IMPROVED -> Decision.KEEP_FOR_NOW
            else -> Decision.RETEST
        }

    /** Read-only. The caller must identify the actual changed file and backup directory.
     * A matching backup is not proof that it belongs to the measured experiment.
     * Existing restore UI must obtain approval and revalidate before writing.
     */
    suspend fun recommend(verdict: OptimizationComparison.Verdict, root: File,
        relative: String, backupDir: File): Decision = withContext(Dispatchers.IO) {
        if (verdict != OptimizationComparison.Verdict.REGRESSED) return@withContext decide(verdict, false)
        decide(verdict, GamePatchStore.findMatchingBackup(root, relative, backupDir) != null)
    }
}
