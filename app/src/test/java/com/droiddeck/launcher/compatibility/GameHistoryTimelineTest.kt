package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameHistoryTimelineTest {
    private fun observation(at: Long, version: String?, outcome: GameRunHistory.Outcome) =
        GameRunHistory.Observation("steam:123", version, outcome, at)

    @Test fun preservesQueryOrderEvenWhenClockChanges() {
        val result = GameHistoryTimeline.rows(listOf(
            observation(10L, "2", GameRunHistory.Outcome.STARTED),
            observation(20L, "1", GameRunHistory.Outcome.BLACK_SCREEN),
            observation(5L, null, GameRunHistory.Outcome.UNKNOWN)
        ))
        assertEquals(listOf(10L, 20L, 5L), result.map { it.recordedAt })
        assertEquals(listOf("2", "1", null), result.map { it.version })
        assertEquals("실행 시작 (플레이 여부 미확인)", result.first().label)
        assertEquals("검은 화면", result[1].label)
        assertEquals("판정 보류", result[2].label)
    }

    @Test fun boundedRowsAndEmptyHistory() {
        val rows = GameHistoryTimeline.rows((30 downTo 1).map {
            observation(it.toLong(), null, GameRunHistory.Outcome.PLAYABLE)
        })
        assertEquals(20, rows.size)
        assertEquals(30L, rows.first().recordedAt)
        assertEquals(11L, rows.last().recordedAt)
        assertTrue(GameHistoryTimeline.rows(emptyList()).isEmpty())
    }

    @Test fun everyOutcomeHasDistinctLabel() {
        val labels = GameRunHistory.Outcome.values().map(GameHistoryTimeline::label)
        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(labels.size, labels.distinct().size)
    }
}
