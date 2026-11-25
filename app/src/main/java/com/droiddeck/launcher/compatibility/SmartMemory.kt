package com.droiddeck.launcher.compatibility

import android.content.ComponentCallbacks2
import android.content.Context

internal object SmartMemory {
    data class Report(val availableBytes: Long?, val totalBytes: Long?, val guidance: String)

    internal fun shouldRelease(level: Int, idle: Boolean): Boolean = idle &&
        (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)

    fun onMemoryPressure(level: Int) {
        if (shouldRelease(level, GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))) {
            com.droiddeck.launcher.input.SteamTouchBindings.releaseCachedIcons()
        }
    }

    fun inspect(context: Context): Report = describe(GameplayBudget.memory(context))

    internal fun describe(snapshot: GameplayBudget.MemorySnapshot?): Report {
        val advice = GameplayBudget.memoryAdvice(snapshot)
        val guidance = when (advice) {
            GameplayBudget.MemoryAdvice.UNKNOWN -> "메모리 상태를 확인할 수 없습니다."
            GameplayBudget.MemoryAdvice.NORMAL -> "현재 시스템 메모리 부족 신호가 없습니다."
            GameplayBudget.MemoryAdvice.DEFER_OPTIONAL_WORK -> "메모리가 부족합니다. 선택적 분석을 보류하고 사용하지 않는 앱을 직접 정리하세요."
        }
        return Report(snapshot?.availableBytes, snapshot?.totalBytes,
            guidance + " RAM Plus 효과는 동일 조건의 반복 측정으로 확인하세요.")
    }
}
