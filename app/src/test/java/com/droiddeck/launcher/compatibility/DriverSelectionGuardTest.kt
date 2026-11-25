package com.droiddeck.launcher.compatibility

import android.content.Context
import com.droiddeck.launcher.session.SessionPhase
import com.droiddeck.launcher.session.SessionState
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
class DriverSelectionGuardTest {
    private lateinit var context: Context
    private val settings get() = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private var oldRunning = false
    private var oldPhase = SessionPhase.IDLE
    private var oldInstalling: String? = null
    private var oldStop = false

    @Before fun setup() {
        oldRunning = SessionState.running
        oldPhase = SessionState.phase
        oldInstalling = SessionState.installing
        oldStop = SessionState.stopRequested
        SessionState.running = false
        SessionState.phase = SessionPhase.IDLE
        SessionState.installing = null
        SessionState.stopRequested = false
        context = RuntimeEnvironment.getApplication()
        assertTrue(settings.edit().clear().commit())
    }

    @After fun restoreSession() {
        SessionState.running = oldRunning
        SessionState.phase = oldPhase
        SessionState.installing = oldInstalling
        SessionState.stopRequested = oldStop
    }

    @Test fun unchangedIdleSelectionAllowed() {
        val expected = DriverSettingsSnapshot.capture(context)
        assertTrue(DriverSettingsSnapshot.canCompleteSelection(context, expected))
    }

    @Test fun allRawSelectorChangesRefuseCompletion() {
        for (key in listOf("linuxDriver", "androidDriver", "gpuDriverMode",
            "linuxDriver.steam", "linuxDriver.desktop")) {
            assertTrue(settings.edit().clear().commit())
            val expected = DriverSettingsSnapshot.capture(context)
            assertTrue(settings.edit().putString(key, "").commit())
            assertFalse(key, DriverSettingsSnapshot.canCompleteSelection(context, expected))
        }
    }

    @Test fun malformedValuesAndMissingBaselineRefuseCompletion() {
        val expected = DriverSettingsSnapshot.capture(context)
        assertFalse(DriverSettingsSnapshot.canCompleteSelection(context, null))
        assertTrue(settings.edit().putInt("linuxDriver", 7).commit())
        assertNull(DriverSettingsSnapshot.captureOrNull(context))
        assertFalse(DriverSettingsSnapshot.canCompleteSelection(context, expected))
    }

    @Test fun allNonIdlePhasesRefuseCompletion() {
        val expected = DriverSettingsSnapshot.capture(context)
        for (phase in SessionPhase.values()) {
            SessionState.phase = phase
            assertEquals(phase.name, phase == SessionPhase.IDLE,
                DriverSettingsSnapshot.canCompleteSelection(context, expected))
        }
    }

    @Test fun runningInstallingOrStoppingRefuseCompletionEvenInIdlePhase() {
        val expected = DriverSettingsSnapshot.capture(context)
        SessionState.running = true
        assertFalse(DriverSettingsSnapshot.canCompleteSelection(context, expected))
        SessionState.running = false
        SessionState.installing = "runtime"
        assertFalse(DriverSettingsSnapshot.canCompleteSelection(context, expected))
        SessionState.installing = null
        SessionState.stopRequested = true
        assertFalse(DriverSettingsSnapshot.canCompleteSelection(context, expected))
        SessionState.stopRequested = false
        assertTrue(DriverSettingsSnapshot.canCompleteSelection(context, expected))
    }
}
