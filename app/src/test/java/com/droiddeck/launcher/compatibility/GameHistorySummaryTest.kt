package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameHistorySummaryTest {
    @Test fun emptyHistoryDoesNotClaimCompatibility() {
        val summary = GameHistorySummary.summarize(emptyList())
        assertEquals(0, summary.total)
        assertTrue(GameHistorySummary.describe(summary).contains("없습니다"))
    }

    @Test fun mixedObservationsKeepUnknownAndStartedInconclusive() {
        val summary = GameHistorySummary.summarize(GameRunHistory.Outcome.values().toList())
        assertEquals(GameHistorySummary.Summary(5, 1, 1, 1, 2), summary)
        assertTrue(GameHistorySummary.describe(summary).contains("보장하지 않습니다"))
    }
}
