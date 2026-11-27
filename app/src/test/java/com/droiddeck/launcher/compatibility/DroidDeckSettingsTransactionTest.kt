package com.droiddeck.launcher.compatibility

import android.content.Context
import com.droiddeck.launcher.session.SessionPrefs
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class DroidDeckSettingsTransactionTest {
    private lateinit var context: Context
    private val prefs get() = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val panel = 2400 to 1080
    private val key = "displayResolution.steam"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        assertTrue(prefs.edit().clear().commit())
    }

    private fun preview() =
        DroidDeckSettingsPreview.outputResolution(context, "steam", panel, "1280x720")

    @Test fun approvedResolutionRestoresAbsentRawPreference() {
        val change = preview()
        assertNull(change.snapshot.raw)
        val receipt = DroidDeckSettingsTransaction.apply(context, change, panel, true)
        assertEquals("1280x720", SessionPrefs.resolutionChoice(context, "steam", panel))
        DroidDeckSettingsTransaction.restore(context, receipt, true)
        assertFalse(prefs.contains(key))
    }

    @Test fun resolutionPreviewCannotBeRetargetedToOtherScope() {
        val change = preview().copy(scope = "desktop")
        assertThrows(IllegalStateException::class.java) {
            DroidDeckSettingsTransaction.apply(context, change, panel, true)
        }
        assertFalse(prefs.contains(key))
        assertFalse(prefs.contains("displayResolution.desktop"))
    }

    @Test fun resolutionPreviewRejectsChangedPanel() {
        val change = preview()
        assertThrows(IllegalArgumentException::class.java) {
            DroidDeckSettingsTransaction.apply(context, change, 1920 to 1080, true)
        }
        assertFalse(prefs.contains(key))
    }

    @Test fun resolutionPreviewRejectsNormalWriteAndRevert() {
        SessionPrefs.setResolutionChoice(context, "steam", "720p")
        val change = preview()
        SessionPrefs.setResolutionChoice(context, "steam", "900p")
        SessionPrefs.setResolutionChoice(context, "steam", "720p")
        assertThrows(IllegalStateException::class.java) {
            DroidDeckSettingsTransaction.apply(context, change, panel, true)
        }
        assertEquals("720p", prefs.getString(key, null))
        assertNull(SettingsBackup.review(context, key))
    }

    @Test fun normalSameValueAndOtherScopeDoNotInvalidatePreview() {
        SessionPrefs.setResolutionChoice(context, "steam", "720p")
        val change = preview()
        SessionPrefs.setResolutionChoice(context, "steam", "720p")
        SessionPrefs.setResolutionChoice(context, "desktop", "900p")
        val receipt = DroidDeckSettingsTransaction.apply(context, change, panel, true)
        DroidDeckSettingsTransaction.restore(context, receipt, true)
        assertEquals("720p", prefs.getString(key, null))
        assertEquals("900p", prefs.getString("displayResolution.desktop", null))
    }

    @Test fun bridgePreviewRejectsNormalWriteAndRevert() {
        SessionPrefs.setBridgeProtonDxvk(context, false)
        val change = DroidDeckSettingsPreview.protonDxvkBridge(context, true)
        SessionPrefs.setBridgeProtonDxvk(context, true)
        SessionPrefs.setBridgeProtonDxvk(context, false)
        assertThrows(IllegalStateException::class.java) {
            DroidDeckSettingsTransaction.apply(context, change, panel, true)
        }
        assertFalse(SessionPrefs.bridgeProtonDxvk(context))
    }

    @Test fun oldPreviewCannotBeReusedAfterApplyAndRestore() {
        val change = preview()
        val receipt = DroidDeckSettingsTransaction.apply(context, change, panel, true)
        DroidDeckSettingsTransaction.restore(context, receipt, true)
        assertThrows(IllegalStateException::class.java) {
            DroidDeckSettingsTransaction.apply(context, change, panel, true)
        }
        assertFalse(prefs.contains(key))
    }

    @Test fun unapprovedApplyAndRestoreRefused() {
        val change = preview()
        assertThrows(IllegalArgumentException::class.java) {
            DroidDeckSettingsTransaction.apply(context, change, panel, false)
        }
        val receipt = DroidDeckSettingsTransaction.apply(context, change, panel, true)
        assertThrows(IllegalArgumentException::class.java) {
            DroidDeckSettingsTransaction.restore(context, receipt, false)
        }
        assertEquals("1280x720", prefs.getString(key, null))
    }

    @Test fun sessionAdmissionBlocksApprovedTransaction() {
        val change = preview()
        requireNotNull(SessionDriverGate.shared.tryAcquire(SessionDriverGate.Kind.SESSION)).use {
            assertThrows(IllegalStateException::class.java) {
                DroidDeckSettingsTransaction.apply(context, change, panel, true)
            }
        }
        assertFalse(prefs.contains(key))
    }
}
