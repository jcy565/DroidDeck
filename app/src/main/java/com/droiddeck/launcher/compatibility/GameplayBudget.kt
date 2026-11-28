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

    private fun sessionBusy(): Boolean = SessionState.running || SessionState.phase != SessionPhase.IDLE ||
        SessionState.installing != null || SessionState.stopRequested

    fun allows(work: Work): Boolean = allows(work, sessionBusy())

    enum class MemoryAdvice { UNKNOWN, NORMAL, DEFER_OPTIONAL_WORK }

    fun memoryAdvice(snapshot: MemorySnapshot?): MemoryAdvice = when {
        snapshot == null || snapshot.totalBytes <= 0 || snapshot.availableBytes < 0 ||
            snapshot.availableBytes > snapshot.totalBytes || snapshot.thresholdBytes < 0 -> MemoryAdvice.UNKNOWN
        snapshot.lowMemory || snapshot.availableBytes <= snapshot.thresholdBytes -> MemoryAdvice.DEFER_OPTIONAL_WORK
        else -> MemoryAdvice.NORMAL
    }

    enum class Admission(val guidance: String) {
        READY("선택적 AI 분석 가능"),
        GAME_BUSY("게임 집중 보호 중 · 실행 및 설치 종료 후 AI 분석을 재개하세요."),
        MEMORY_UNKNOWN("메모리 상태 미확인 · AI 분석 보류"),
        MEMORY_LOW("시스템 메모리 부족 · AI 분석 보류"),
        MODEL_HEADROOM("로컬 모델용 여유 메모리 2 GiB 미만 · 로컬 추론 보류")
    }

    internal fun admission(busy: Boolean, snapshot: MemorySnapshot?, localModel: Boolean): Admission {
        if (busy) return Admission.GAME_BUSY
        return when (memoryAdvice(snapshot)) {
            MemoryAdvice.UNKNOWN -> Admission.MEMORY_UNKNOWN
            MemoryAdvice.DEFER_OPTIONAL_WORK -> Admission.MEMORY_LOW
            MemoryAdvice.NORMAL -> if (localModel && requireNotNull(snapshot).availableBytes < 2L * 1024 * 1024 * 1024)
                Admission.MODEL_HEADROOM else Admission.READY
        }
    }

    fun admission(context: Context, localModel: Boolean = false): Admission {
        if (sessionBusy()) return Admission.GAME_BUSY
        val snapshot = memory(context)
        return admission(sessionBusy(), snapshot, localModel)
    }

    fun allowsOptionalAnalysis(context: Context): Boolean =
        admission(context) == Admission.READY

    /** On demand only: no polling, process enumeration or process killing. */
    fun memory(context: Context): MemorySnapshot? {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        val info = ActivityManager.MemoryInfo()
        try { manager.getMemoryInfo(info) } catch (_: RuntimeException) { return null }
        return MemorySnapshot(info.availMem, info.totalMem, info.threshold, info.lowMemory)
    }
}
