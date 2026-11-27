package com.droiddeck.launcher.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStartupDecisionTest {
    @Test fun onlyStoppingRequiresTeardownWait() {
        for (phase in SessionPhase.values()) {
            assertEquals(phase.name, phase == SessionPhase.STOPPING,
                SessionStartupDecision.waitForTeardown(phase))
        }
    }
}
