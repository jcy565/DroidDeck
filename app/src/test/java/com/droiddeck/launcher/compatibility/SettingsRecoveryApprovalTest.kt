package com.droiddeck.launcher.compatibility

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SettingsRecoveryApprovalTest {
    private lateinit var context: Context
    private val key = "displayResolution.steam"
    private val prefs get() = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        assertTrue(prefs.edit().clear().commit())
    }

    private fun entry() = SettingsBackup.reviewAll(context).single { it.key == key }

    @Test fun prepareIsReadOnlyAndShowsAbsentTarget() {
        SettingsBackup.apply(context, key, null, "1280x720")
        val before = prefs.all
        val request = SettingsRecoveryApproval.prepare(entry(), SettingsBackup.ReviewAction.RESTORE)
        assertNull(request.targetRaw)
        assertEquals(before, prefs.all)
        assertThrows(IllegalArgumentException::class.java) {
            SettingsRecoveryApproval.execute(context, request, false)
        }
        assertEquals(before, prefs.all)
    }

    @Test fun approvedRestoreConsumesRecordAndCannotRepeat() {
        SettingsBackup.apply(context, key, null, "1280x720")
        val request = SettingsRecoveryApproval.prepare(entry(), SettingsBackup.ReviewAction.RESTORE)
        SettingsRecoveryApproval.execute(context, request, true)
        assertFalse(prefs.contains(key))
        assertNull(entry().review)
        assertThrows(IllegalStateException::class.java) {
            SettingsRecoveryApproval.execute(context, request, true)
        }
    }

    @Test fun approvalRefusesChangedAndRevertedConflict() {
        SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        val request = SettingsRecoveryApproval.prepare(entry(), SettingsBackup.ReviewAction.KEEP_CURRENT_AND_ARCHIVE)
        SettingsBackup.writeNormalResolution(context, "steam", "900p")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        val before = prefs.all
        assertThrows(IllegalStateException::class.java) {
            SettingsRecoveryApproval.execute(context, request, true)
        }
        assertEquals(before, prefs.all)
    }

    @Test fun approvedArchivePreservesCurrentAndConsumesPendingRecord() {
        SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        val request = SettingsRecoveryApproval.prepare(entry(), SettingsBackup.ReviewAction.KEEP_CURRENT_AND_ARCHIVE)
        assertEquals("1600x900", request.targetRaw)
        SettingsRecoveryApproval.execute(context, request, true)
        assertEquals("1600x900", prefs.getString(key, null))
        assertEquals(request.reviewed, entry().archived)
        assertNull(entry().review)
    }

    @Test fun conflictCannotPrepareRestore() {
        SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        assertThrows(IllegalArgumentException::class.java) {
            SettingsRecoveryApproval.prepare(entry(), SettingsBackup.ReviewAction.RESTORE)
        }
    }

    @Test fun occupiedArchiveAfterReviewPreventsExecution() {
        SettingsBackup.apply(context, key, null, "1280x720")
        val request = SettingsRecoveryApproval.prepare(entry(), SettingsBackup.ReviewAction.KEEP_CURRENT_AND_ARCHIVE)
        prefs.edit().putBoolean("ai.archive.$key.currentPresent", false).commit()
        val before = prefs.all
        assertThrows(IllegalStateException::class.java) {
            SettingsRecoveryApproval.execute(context, request, true)
        }
        assertEquals(before, prefs.all)
    }

    @Test fun sessionLeasePreventsConfirmedRestore() {
        SettingsBackup.apply(context, key, null, "1280x720")
        val request = SettingsRecoveryApproval.prepare(entry(), SettingsBackup.ReviewAction.RESTORE)
        val before = prefs.all
        requireNotNull(SessionDriverGate.shared.tryAcquire(SessionDriverGate.Kind.SESSION)).use {
            assertThrows(IllegalStateException::class.java) {
                SettingsRecoveryApproval.execute(context, request, true)
            }
        }
        assertEquals(before, prefs.all)
    }
}
