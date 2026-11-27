package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameHistoryQueryTest {
    @Test fun versionScopeUsesBoundParametersAndClampsLimit() {
        val query = GameHistoryQuery.recent("steam:1", 200, "v' OR 1=1 --")
        assertEquals("game_id = ? AND game_version = ?", query.where)
        assertEquals(listOf("steam:1", "v' OR 1=1 --"), query.args)
        assertEquals(100, query.limit)
    }

    @Test fun allVersionsUsesOnlyGameId() {
        val query = GameHistoryQuery.recent("steam:1", 0, null)
        assertEquals("game_id = ?", query.where)
        assertEquals(listOf("steam:1"), query.args)
        assertEquals(1, query.limit)
    }

    @Test fun malformedVersionCannotSilentlyBroadenQuery() {
        for (bad in listOf("", " ", "a\nb", "x".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) {
                GameHistoryQuery.recent("steam:1", 20, bad)
            }
        }
    }
}
