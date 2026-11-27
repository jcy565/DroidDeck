package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameObservationDraftTest {
    private val outcome = GameRunHistory.Outcome.BLACK_SCREEN
    private val explicit = GameObservationDraft.Attribution.OBSERVED_INSTALL
    private val unknown = GameObservationDraft.Attribution.UNKNOWN
    private val draft = GameObservationDraft.prepare("steam:620", "123", outcome)

    @Test fun explicitVersionRetainsExactObservation() {
        assertEquals(GameRunHistory.Observation("steam:620", "123", outcome, 42),
            GameObservationDraft.confirm(draft, "steam:620", "123", explicit, 42))
    }

    @Test fun unknownAttributionDoesNotBorrowInstalledVersion() {
        assertNull(GameObservationDraft.confirm(draft, "steam:620", "123", unknown, 43).gameVersion)
    }

    @Test fun missingInstalledVersionCannotBeExplicitlyAttributed() {
        val absent = GameObservationDraft.prepare("steam:620", null, outcome)
        assertThrows(IllegalArgumentException::class.java) {
            GameObservationDraft.confirm(absent, "steam:620", null, explicit, 0)
        }
        assertNull(GameObservationDraft.confirm(absent, "steam:620", null, unknown, 0).gameVersion)
    }

    @Test fun changedGameInvalidatesDraft() {
        assertThrows(IllegalArgumentException::class.java) {
            GameObservationDraft.confirm(draft, "steam:621", "123", unknown, 0)
        }
    }

    @Test fun changedOrRemovedVersionInvalidatesDraft() {
        assertThrows(IllegalArgumentException::class.java) {
            GameObservationDraft.confirm(draft, "steam:620", "124", explicit, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameObservationDraft.confirm(draft, "steam:620", null, unknown, 0)
        }
    }

    @Test fun rejectsMalformedVersionAutomaticOutcomeAndNegativeTimestamp() {
        assertThrows(IllegalArgumentException::class.java) {
            GameObservationDraft.prepare("steam:620", "", outcome)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameObservationDraft.prepare("steam:620", null, GameRunHistory.Outcome.STARTED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameObservationDraft.confirm(draft, "steam:620", "123", explicit, -1)
        }
    }
}
