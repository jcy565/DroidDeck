package com.droiddeck.launcher.session

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SessionLaunchMetadataTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        SessionPaths.take()
        SessionState.running = false
        SessionState.phase = SessionPhase.IDLE
    }

    @After fun cleanup() {
        SessionPaths.take()?.let { SessionPaths.release(context, it) }
        SessionState.running = false
        SessionState.phase = SessionPhase.IDLE
        SessionState.program = null
        SessionState.programArgs = emptyList()
        SessionState.steamUi = null
        SessionState.steamUrl = null
        SessionState.steamVr = false
        SessionState.sessionId = null
        SessionState.logDirectory = null
        SessionState.logFile = null
        SessionState.eventsFile = null
        SessionState.clearRelaunch()
    }

    @Test fun newLaunchRetainsMetadataWhenPreparingIsPublished() {
        val launch = SessionEvents.LaunchMetadata(
            program = "example-program",
            programArgs = listOf("--example", "value"),
            steamUi = "desktop",
            steamUrl = "steam://rungameid/123",
            steamVr = true,
        )
        SessionEvents.begin(context, SessionService.MODE_STEAM, "test-launch", launch)
        assertEquals(SessionPhase.PREPARING, SessionState.phase)
        assertEquals(launch.program, SessionState.program)
        assertEquals(launch.programArgs, SessionState.programArgs)
        assertEquals(launch.steamUi, SessionState.steamUi)
        assertEquals(launch.steamUrl, SessionState.steamUrl)
        assertTrue(SessionState.steamVr)
    }

    @Test fun defaultBeginClearsPreviousLaunchMetadata() {
        SessionState.program = "previous"
        SessionState.programArgs = listOf("previous-arg")
        SessionState.steamUi = "previous-ui"
        SessionState.steamUrl = "steam://rungameid/123"
        SessionState.steamVr = true
        SessionEvents.begin(context, SessionService.MODE_STEAM, "test-default")
        assertEquals(SessionPhase.PREPARING, SessionState.phase)
        assertNull(SessionState.program)
        assertTrue(SessionState.programArgs.isEmpty())
        assertNull(SessionState.steamUi)
        assertNull(SessionState.steamUrl)
        assertFalse(SessionState.steamVr)
    }

    @Test fun launchArgumentsAreCopiedAndDoNotTrackCallerMutations() {
        val args = mutableListOf("original")
        SessionEvents.begin(context, SessionService.MODE_STEAM, "test-args",
            SessionEvents.LaunchMetadata(programArgs = args))
        args[0] = "changed"
        assertEquals(listOf("original"), SessionState.programArgs)
    }

    @Test fun newLaunchResetsOldFailureAndCancellationState() {
        SessionState.failureCode = "OLD"
        SessionState.failureMessage = "old error"
        SessionState.failureStatus = 1
        SessionState.stopRequested = true
        SessionState.phase = SessionPhase.FAILED
        SessionEvents.begin(context, SessionService.MODE_STEAM, "test-restart",
            SessionEvents.LaunchMetadata(program = "new"))
        assertEquals(SessionPhase.PREPARING, SessionState.phase)
        assertNull(SessionState.failureCode)
        assertNull(SessionState.failureMessage)
        assertNull(SessionState.failureStatus)
        assertFalse(SessionState.stopRequested)
        assertEquals("new", SessionState.program)
    }
}
