package com.droiddeck.launcher.compatibility

import android.app.ActivityManager
import android.content.Context
import com.droiddeck.launcher.session.SessionPhase
import com.droiddeck.launcher.session.SessionState

internal object GameplayBudget {
    enum class Work { ANALYSIS, STORAGE_SCAN, SETTINGS_WRITE, PATCH_WRITE, EXPLICIT_SAMPLE }
    data class MemorySnapshot(val availableBytes: Long, val totalBytes: Long,
        val thresholdBytes: Long, val lowMemory: Boolean)

    internal fun allows(work: Work, busy: Boolean): Boolean =
        !busy || work == Work.EXPLICIT_SAMPLE

    fun allows(work: Work): Boolean = allows(work,
        SessionState.running || SessionState.phase != SessionPhase.IDLE ||
            SessionState.installing != null || SessionState.stopRequested)

    /** On demand only: no polling, process enumeration or process killing. */
    fun memory(context: Context): MemorySnapshot? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return MemorySnapshot(info.availMem, info.totalMem, info.threshold, info.lowMemory)
    }
}
