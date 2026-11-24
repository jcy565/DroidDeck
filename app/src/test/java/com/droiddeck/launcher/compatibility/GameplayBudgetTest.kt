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
    @Test fun unavailableMemory() = assertEquals(GameplayBudget.MemoryAdvice.UNKNOWN, GameplayBudget.memoryAdvice(null))
}
