package com.droiddeck.launcher.session

/** New startup must not replace the shared state during asynchronous teardown. */
internal object SessionStartupDecision {
    fun waitForTeardown(phase: SessionPhase): Boolean = phase == SessionPhase.STOPPING
}
