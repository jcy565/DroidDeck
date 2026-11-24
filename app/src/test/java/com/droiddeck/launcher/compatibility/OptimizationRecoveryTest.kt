package com.droiddeck.launcher.compatibility

import org.junit.Assert.assertEquals
import org.junit.Test

class OptimizationRecoveryTest {
    @Test fun regressionWithBackup() = assertEquals(OptimizationRecovery.Decision.REVIEW_RESTORE,
        OptimizationRecovery.decide(OptimizationComparison.Verdict.REGRESSED, true))
    @Test fun regressionWithoutBackup() = assertEquals(OptimizationRecovery.Decision.NO_MATCHING_BACKUP,
        OptimizationRecovery.decide(OptimizationComparison.Verdict.REGRESSED, false))
    @Test fun improvementKeepsSettings() = assertEquals(OptimizationRecovery.Decision.KEEP_FOR_NOW,
        OptimizationRecovery.decide(OptimizationComparison.Verdict.IMPROVED, true))
    @Test fun uncertainResultsRequireRetest() {
        for (verdict in listOf(OptimizationComparison.Verdict.MIXED,
            OptimizationComparison.Verdict.INCONCLUSIVE, OptimizationComparison.Verdict.INCOMPARABLE)) {
            assertEquals(OptimizationRecovery.Decision.RETEST, OptimizationRecovery.decide(verdict, true))
        }
    }
}
