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
}
