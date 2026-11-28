package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameplayBudgetTest {
    @Test fun idleAllowsOptionalWork() {
        GameplayBudget.Work.values().forEach { assertTrue(GameplayBudget.allows(it, false)) }
    }
    @Test fun busyAllowsOnlyExplicitSampling() {
        GameplayBudget.Work.values().forEach {
            assertEquals(it == GameplayBudget.Work.EXPLICIT_SAMPLE, GameplayBudget.allows(it, true))
        }
    }
    private val snapshot = GameplayBudget.MemorySnapshot(200, 1000, 100, false)
    @Test fun normalMemory() = assertEquals(GameplayBudget.MemoryAdvice.NORMAL, GameplayBudget.memoryAdvice(snapshot))
    @Test fun systemLowMemory() = assertEquals(GameplayBudget.MemoryAdvice.DEFER_OPTIONAL_WORK, GameplayBudget.memoryAdvice(snapshot.copy(lowMemory = true)))
    @Test fun thresholdReached() = assertEquals(GameplayBudget.MemoryAdvice.DEFER_OPTIONAL_WORK, GameplayBudget.memoryAdvice(snapshot.copy(availableBytes = 100)))
    @Test fun invalidMemory() = assertEquals(GameplayBudget.MemoryAdvice.UNKNOWN, GameplayBudget.memoryAdvice(snapshot.copy(availableBytes = 1001)))
    @Test fun admissionExplainsSessionAndMemoryLimits() {
        val good = GameplayBudget.MemorySnapshot(3L shl 30, 8L shl 30, 512L shl 20, false)
        assertEquals(GameplayBudget.Admission.GAME_BUSY, GameplayBudget.admission(true, good, false))
        assertEquals(GameplayBudget.Admission.MEMORY_UNKNOWN, GameplayBudget.admission(false, null, false))
        assertEquals(GameplayBudget.Admission.MEMORY_LOW, GameplayBudget.admission(false, good.copy(lowMemory = true), true))
        val limited = good.copy(availableBytes = 1L shl 30)
        assertEquals(GameplayBudget.Admission.MODEL_HEADROOM, GameplayBudget.admission(false, limited, true))
        assertEquals(GameplayBudget.Admission.READY, GameplayBudget.admission(false, limited, false))
        assertEquals(GameplayBudget.Admission.READY, GameplayBudget.admission(false, good.copy(availableBytes = 2L shl 30), true))
    }

    @Test fun unavailableMemory() = assertEquals(GameplayBudget.MemoryAdvice.UNKNOWN, GameplayBudget.memoryAdvice(null))
}
