package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameLogReportTest {
    @Test fun logSignalDoesNotCreateAnOutcome() {
        val history = GameCompatibilityService.analyze(emptyList())
        val result = GameCompatibilityService.combine(history, "VK_ERROR_DEVICE_LOST")
        assertEquals(0, result.history.summary.total)
        assertTrue(result.logDiagnosis.contains("VK_ERROR_DEVICE_LOST"))
        assertTrue(result.limitation.contains("확정하지 않습니다"))
    }

    @Test fun unknownLogDoesNotEraseHistory() {
        val history = GameCompatibilityService.analyze(listOf(
            GameRunHistory.Observation("steam:1", null, GameRunHistory.Outcome.BLACK_SCREEN, 1)
        ))
        val result = GameCompatibilityService.combine(history, "ordinary log")
        assertEquals(1, result.history.summary.blackScreen)
        assertTrue(result.logDiagnosis.contains("확정하기 어렵습니다"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedLog() {
        GameCompatibilityService.combine(GameCompatibilityService.analyze(emptyList()), "x".repeat(1_048_577))
    }
}
