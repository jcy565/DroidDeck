package com.droiddeck.launcher.session

import android.content.Intent
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SessionRelaunchOwnershipTest {
    @Before fun setup() {
        SessionState.clearRelaunch()
        SessionState.sessionId = "old-session"
    }

    @After fun cleanup() {
        SessionState.clearRelaunch()
        SessionState.sessionId = null
    }

    @Test fun delayedOldCallbackCannotConsumeNewRequest() {
        SessionState.sessionId = "new-session"
        val next = Intent("new-launch")
        SessionState.queueRelaunch(next)
        SessionState.confirmRelaunchStop()
        assertNull(SessionState.takeRelaunch("old-session"))
        assertSame(next, SessionState.takeRelaunch("new-session"))
        assertNull(SessionState.takeRelaunch("new-session"))
    }

    @Test fun missingCallbackIdentityCannotConsumeRequest() {
        val next = Intent("launch")
        SessionState.queueRelaunch(next)
        SessionState.confirmRelaunchStop()
        assertNull(SessionState.takeRelaunch(null))
        assertSame(next, SessionState.takeRelaunch("old-session"))
    }

    @Test fun replacedRequestRequiresItsOwnStopConfirmation() {
        SessionState.queueRelaunch(Intent("first"))
        SessionState.confirmRelaunchStop()
        SessionState.queueRelaunch(Intent("second"))
        assertNull(SessionState.takeRelaunch("old-session"))
    }

    @Test fun oldQueuedRequestIsNotTransferredToAnotherSession() {
        SessionState.queueRelaunch(Intent("first"))
        SessionState.confirmRelaunchStop()
        SessionState.sessionId = "new-session"
        assertNull(SessionState.takeRelaunch("new-session"))
    }
}
