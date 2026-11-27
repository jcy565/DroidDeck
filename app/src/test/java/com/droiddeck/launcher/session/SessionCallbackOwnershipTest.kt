package com.droiddeck.launcher.session

import org.junit.Assert.*
import org.junit.Test

class SessionCallbackOwnershipTest {
    @Test fun missingOrDifferentIdentityNeverOwnsCallback() {
        assertFalse(SessionCallbackOwnership.owns(null, null))
        assertFalse(SessionCallbackOwnership.owns(null, "current"))
        assertFalse(SessionCallbackOwnership.owns("old", null))
        assertFalse(SessionCallbackOwnership.owns("old", "current"))
        assertTrue(SessionCallbackOwnership.owns("current", "current"))
    }

    @Test fun activeCallbacksRequireCurrentIdentityAndNonterminalPhase() {
        for (phase in SessionPhase.values()) {
            val active = phase !in setOf(SessionPhase.IDLE, SessionPhase.FAILED, SessionPhase.STOPPING)
            assertEquals(phase.name, active, SessionCallbackOwnership.acceptsActive("current", "current", phase))
            assertFalse(SessionCallbackOwnership.acceptsActive("old", "current", phase))
            assertFalse(SessionCallbackOwnership.acceptsActive(null, null, phase))
        }
    }
}
