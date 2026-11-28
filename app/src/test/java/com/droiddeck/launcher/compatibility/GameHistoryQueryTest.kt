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
    @Test fun invalidIdentityAndMalformedUnicodeAreRejected() {
        for (bad in listOf(" ", "steam:1\u0000", "steam:1\n", "x".repeat(257), "bad" + 0xd800.toChar())) {
            assertThrows(IllegalArgumentException::class.java) { GameHistoryQuery.recent(bad, 20, null) }
        }
        val badVersion = "v" + 0xdc00.toChar()
        assertFalse(GameHistoryQuery.validVersion(badVersion))
        assertNull(GameHistoryQuery.observationVersion(badVersion, true))
        assertEquals(listOf("steam:1", "v😀"), GameHistoryQuery.recent("steam:1", 20, "v😀").args)
    }

    @Test fun returnedRowsMustStayWithinRequestedGameAndVersion() {
        GameHistoryQuery.requireScopedRow("steam:1", null, "steam:1", null)
        GameHistoryQuery.requireScopedRow("steam:1", null, "steam:1", "v2")
        GameHistoryQuery.requireScopedRow("steam:1", "v1", "steam:1", "v1")
        for (row in listOf("steam:2" to "v1", "steam:1" to null, "steam:1" to "v2")) {
            assertThrows(IllegalArgumentException::class.java) {
                GameHistoryQuery.requireScopedRow("steam:1", "v1", row.first, row.second)
            }
        }
    }

    @Test fun versionMustBeConfirmedForManualObservation() {
        assertNull(GameHistoryQuery.observationVersion("1.2", false))
        assertEquals("1.2", GameHistoryQuery.observationVersion("1.2", true))
        assertNull(GameHistoryQuery.observationVersion(null, true))
        for (invalid in listOf("", " ", "v\n1", "v".repeat(129)))
            assertNull(GameHistoryQuery.observationVersion(invalid, true))
    }
}
