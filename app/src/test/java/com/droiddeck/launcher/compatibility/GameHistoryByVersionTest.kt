package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameHistoryByVersionTest {
    private fun observation(version: String?, outcome: GameRunHistory.Outcome, at: Long = 0) =
        GameRunHistory.Observation("steam:620", version, outcome, at)

    @Test fun groupsInNewestObservedOrderAndKeepsUnknownSeparate() {
        val rows = listOf(
            observation("2", GameRunHistory.Outcome.BLACK_SCREEN, 5),
            observation(null, GameRunHistory.Outcome.UNKNOWN, 4),
            observation("1", GameRunHistory.Outcome.PLAYABLE, 3),
            observation("2", GameRunHistory.Outcome.CRASH, 2),
            observation(null, GameRunHistory.Outcome.PLAYABLE, 1)
        )
        val groups = GameHistoryByVersion.summarize(rows)
        assertEquals(listOf("2", null, "1"), groups.map { it.version })
        assertEquals(1, groups[0].summary.blackScreen)
        assertEquals(1, groups[0].summary.crashes)
        assertEquals(1, groups[1].summary.inconclusive)
        assertEquals(1, groups[1].summary.playable)
        assertEquals(1, groups[2].summary.playable)
        assertEquals(rows.size, groups.sumOf { it.summary.total })
    }

    @Test fun emptyHistoryAndExactVersionLabels() {
        assertTrue(GameHistoryByVersion.summarize(emptyList()).isEmpty())
        val groups = GameHistoryByVersion.summarize(listOf(
            observation("1.0", GameRunHistory.Outcome.PLAYABLE),
            observation("1.00", GameRunHistory.Outcome.STARTED)
        ))
        assertEquals(listOf("1.0", "1.00"), groups.map { it.version })
        assertEquals(1, groups[1].summary.inconclusive)
    }

    @Test fun rejectsMixedGameRows() {
        val first = observation("1", GameRunHistory.Outcome.PLAYABLE)
        assertThrows(IllegalArgumentException::class.java) {
            GameHistoryByVersion.summarize(listOf(first, first.copy(gameId = "steam:621")))
        }
    }
}
