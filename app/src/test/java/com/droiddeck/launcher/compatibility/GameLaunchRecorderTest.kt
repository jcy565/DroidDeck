package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class GameLaunchRecorderTest {
    @Test fun publishedRequestIsNotProofOfStartup() {
        val entry = requireNotNull(GameLaunchRecorder.requestObservation("123", 10))
        assertEquals("steam:123", entry.gameId)
        assertEquals(GameRunHistory.Outcome.UNKNOWN, entry.outcome)
        assertEquals(10L, entry.recordedAt)
    }

    @Test fun rejectsInvalidIdentifiersAndTime() {
        assertNull(GameLaunchRecorder.requestObservation("../123", 10))
        assertNull(GameLaunchRecorder.requestObservation("123", -1))
    }
}
