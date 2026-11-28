package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameCompatibilityServiceTest {
    @Test fun emptyHistoryHasNoPositiveVerdict() {
        val report = GameCompatibilityService.analyze(emptyList())
        assertEquals(0, report.summary.total)
        assertTrue(report.advice.single().contains("판단할 수 없습니다"))
    }

    @Test fun latestFailureGuidesAdviceWithoutErasingOlderSuccess() {
        val observations = listOf(
            GameRunHistory.Observation("steam:1", null, GameRunHistory.Outcome.BLACK_SCREEN, 2),
            GameRunHistory.Observation("steam:1", null, GameRunHistory.Outcome.PLAYABLE, 1)
        )
        val report = GameCompatibilityService.analyze(observations)
        assertEquals(1, report.summary.playable)
        assertEquals(1, report.summary.blackScreen)
        assertEquals(GameHistoryAdvice.recommend(GameRunHistory.Outcome.BLACK_SCREEN), report.advice)
    }

    @Test fun logDiagnosisDoesNotAlterVersionedHistoryOrInventPlayability() {
        val history = GameCompatibilityService.analyze(listOf(
            GameRunHistory.Observation("steam:1", "v2", GameRunHistory.Outcome.BLACK_SCREEN, 2)
        ))
        val combined = GameCompatibilityService.combine(history, "reason LOW_MEMORY")
        assertSame(history, combined.history)
        assertEquals(0, combined.history.summary.playable)
        assertEquals("v2", combined.history.observations.single().gameVersion)
        assertTrue(combined.logDiagnosis.contains("LOW_MEMORY"))
        assertTrue(combined.limitation.contains("별개 증거"))
        assertTrue(GameCompatibilityService.combine(GameCompatibilityService.analyze(emptyList()),
            "reason LOW_MEMORY").history.observations.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMixedGameReports() {
        GameCompatibilityService.analyze(listOf(
            GameRunHistory.Observation("steam:1", null, GameRunHistory.Outcome.STARTED, 2),
            GameRunHistory.Observation("steam:2", null, GameRunHistory.Outcome.STARTED, 1)
        ))
    }
}
