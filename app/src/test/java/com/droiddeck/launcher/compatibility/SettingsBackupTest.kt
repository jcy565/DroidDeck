package com.droiddeck.launcher.compatibility

import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SettingsBackupTest {
    private lateinit var context: Context
    private val key = "displayResolution.steam"
    private val prefs get() = context.getSharedPreferences("session", Context.MODE_PRIVATE)

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        assertTrue(prefs.edit().clear().commit())
    }

    @Test fun restoresAbsentKey() {
        SettingsBackup.apply(context, key, null, "1280x720")
        assertEquals("1280x720", SettingsBackup.raw(context, key))
        SettingsBackup.restore(context, key, true)
        assertFalse(prefs.contains(key))
    }

    @Test fun restoresExactLegacyValue() {
        prefs.edit().putString(key, "900p").commit()
        SettingsBackup.apply(context, key, "900p", "1280x720")
        SettingsBackup.restore(context, key, true)
        assertEquals("900p", prefs.getString(key, null))
    }

    @Test fun restoresBooleanType() {
        val bridge = "bridgeProtonDxvk"
        prefs.edit().putBoolean(bridge, false).commit()
        SettingsBackup.apply(context, bridge, "false", "true")
        assertTrue(prefs.getBoolean(bridge, false))
        SettingsBackup.restore(context, bridge, true)
        assertFalse(prefs.getBoolean(bridge, true))
    }

    @Test fun rejectsStalePreview() {
        prefs.edit().putString(key, "900p").commit()
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.apply(context, key, null, "1280x720")
        }
        assertEquals("900p", SettingsBackup.raw(context, key))
    }

    @Test fun preservesOriginalBackup() {
        SettingsBackup.apply(context, key, null, "1280x720")
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.apply(context, key, "1280x720", "1600x900")
        }
        SettingsBackup.restore(context, key, true)
        assertFalse(prefs.contains(key))
    }

    @Test fun refusesConflictingRestore() {
        SettingsBackup.apply(context, key, null, "1280x720")
        prefs.edit().putString(key, "1600x900").commit()
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restore(context, key, true)
        }
        assertEquals("1600x900", SettingsBackup.raw(context, key))
    }

    @Test fun refusesUnapprovedRestore() {
        SettingsBackup.apply(context, key, null, "1280x720")
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.restore(context, key, false)
        }
        assertEquals("1280x720", SettingsBackup.raw(context, key))
    }

    @Test fun reviewRecoversDurableRecordWithoutReceipt() {
        prefs.edit().putString(key, "900p").commit()
        SettingsBackup.apply(context, key, "900p", "1280x720")
        val review = requireNotNull(SettingsBackup.review(context, key))
        assertEquals("900p", review.before)
        assertEquals("1280x720", review.applied)
        assertTrue(review.matchesApplied)
        SettingsBackup.restoreReviewed(context, review, true)
        assertEquals("900p", SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))
    }

    @Test fun reviewShowsConflictWithoutOverwriting() {
        SettingsBackup.apply(context, key, null, "1280x720")
        val approvedReview = requireNotNull(SettingsBackup.review(context, key))
        prefs.edit().putString(key, "1600x900").commit()
        assertFalse(requireNotNull(SettingsBackup.review(context, key)).matchesApplied)
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restoreReviewed(context, approvedReview, true)
        }
        assertEquals("1600x900", SettingsBackup.raw(context, key))
    }

    @Test fun changedBaselineInvalidatesPreviouslyReviewedRestore() {
        SettingsBackup.apply(context, key, null, "1280x720")
        val old = requireNotNull(SettingsBackup.review(context, key))
        prefs.edit().putBoolean("ai.backup.$key.present", true)
            .putString("ai.backup.$key.before", "900p").commit()
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restoreReviewed(context, old, true)
        }
        assertEquals("1280x720", SettingsBackup.raw(context, key))
    }

    @Test fun incompleteJournalIsNotOverwritten() {
        prefs.edit().putBoolean("ai.backup.$key.present", true).commit()
        assertNull(SettingsBackup.review(context, key))
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.apply(context, key, null, "1280x720")
        }
        assertTrue(prefs.contains("ai.backup.$key.present"))
        assertFalse(prefs.contains(key))
    }

    @Test fun malformedRecordCannotRestore() {
        SettingsBackup.apply(context, key, null, "1280x720")
        prefs.edit().putString("ai.backup.$key.before", "unexpected").commit()
        assertNull(SettingsBackup.review(context, key))
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restore(context, key, true)
        }
        assertEquals("1280x720", SettingsBackup.raw(context, key))
    }

    @Test fun malformedBooleanBaselineCannotRestore() {
        val bridge = "bridgeProtonDxvk"
        SettingsBackup.apply(context, bridge, null, "true")
        prefs.edit().putBoolean("ai.backup.$bridge.present", true)
            .putString("ai.backup.$bridge.before", "maybe").commit()
        assertNull(SettingsBackup.review(context, bridge))
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restore(context, bridge, true)
        }
        assertTrue(prefs.getBoolean(bridge, false))
    }

    @Test fun sessionAdmissionBlocksApplyAndRestore() {
        val gate = SessionDriverGate.shared
        requireNotNull(gate.tryAcquire(SessionDriverGate.Kind.SESSION)).use {
            assertThrows(IllegalStateException::class.java) {
                SettingsBackup.apply(context, key, null, "1280x720")
            }
        }
        SettingsBackup.apply(context, key, null, "1280x720")
        val review = requireNotNull(SettingsBackup.review(context, key))
        requireNotNull(gate.tryAcquire(SessionDriverGate.Kind.SESSION)).use {
            assertThrows(IllegalStateException::class.java) {
                SettingsBackup.restoreReviewed(context, review, true)
            }
        }
        SettingsBackup.restoreReviewed(context, review, true)
        assertFalse(prefs.contains(key))
    }

    @Test fun noOpDoesNotCreateBackup() {
        prefs.edit().putString(key, "1280x720").commit()
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.apply(context, key, "1280x720", "1280x720")
        }
        assertNull(SettingsBackup.review(context, key))
    }
}
