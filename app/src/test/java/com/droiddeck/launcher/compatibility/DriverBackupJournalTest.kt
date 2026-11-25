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
class DriverBackupJournalTest {
    private lateinit var context: Context
    private val settings get() = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private val journal get() = context.getSharedPreferences("ai_driver_backup", Context.MODE_PRIVATE)
    private fun snapshot() = DriverSettingsSnapshot.capture(context)

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        assertTrue(settings.edit().clear().commit())
        assertTrue(journal.edit().clear().commit())
    }

    @Test fun emptyJournal() {
        assertNull(DriverBackupJournal.load(context))
    }

    @Test fun preservesExactNullableValues() {
        settings.edit().putString("linuxDriver.steam", "legacy").putString("androidDriver", "").commit()
        val before = snapshot()
        DriverBackupJournal.save(context, before, true)
        settings.edit().putString("linuxDriver", "new").commit()
        assertEquals(before, DriverBackupJournal.load(context))
        assertEquals(setOf("linuxDriver"), DriverSettingsSnapshot.changed(before, snapshot()))
        assertEquals("new", settings.getString("linuxDriver", null))
    }

    @Test fun rejectsDuplicate() {
        val before = snapshot()
        DriverBackupJournal.save(context, before, true)
        assertThrows(IllegalStateException::class.java) {
            DriverBackupJournal.save(context, before, true)
        }
        assertEquals(before, DriverBackupJournal.load(context))
    }

    @Test fun rejectsStaleSnapshot() {
        val before = snapshot()
        settings.edit().putString("gpuDriverMode", "changed").commit()
        assertThrows(IllegalStateException::class.java) {
            DriverBackupJournal.save(context, before, true)
        }
        assertNull(DriverBackupJournal.load(context))
    }

    @Test fun rejectsWithoutApproval() {
        assertThrows(IllegalArgumentException::class.java) {
            DriverBackupJournal.save(context, snapshot(), false)
        }
        assertNull(DriverBackupJournal.load(context))
    }

    @Test fun rejectsIncompleteJournal() {
        journal.edit().putString("baseline", "{}").commit()
        assertThrows(IllegalArgumentException::class.java) {
            DriverBackupJournal.load(context)
        }
    }
    @Test fun recordsAppliedAndRejectsLaterConflict() {
        DriverBackupJournal.save(context, snapshot(), true)
        settings.edit().putString("linuxDriver", "new").commit()
        val applied = snapshot()
        assertFalse(DriverBackupJournal.canReviewRestore(context, applied))
        DriverBackupJournal.recordApplied(context, applied, true)
        assertEquals(applied, DriverBackupJournal.loadApplied(context))
        assertTrue(DriverBackupJournal.canReviewRestore(context, applied))
        settings.edit().putString("androidDriver", "external").commit()
        assertFalse(DriverBackupJournal.canReviewRestore(context, applied))
        assertFalse(DriverBackupJournal.canReviewRestore(context, snapshot()))
    }

    @Test fun rejectsUnapprovedAndDuplicateAppliedRecord() {
        DriverBackupJournal.save(context, snapshot(), true)
        settings.edit().putString("linuxDriver", "new").commit()
        val applied = snapshot()
        assertThrows(IllegalArgumentException::class.java) {
            DriverBackupJournal.recordApplied(context, applied, false)
        }
        assertNull(DriverBackupJournal.loadApplied(context))
        DriverBackupJournal.recordApplied(context, applied, true)
        assertThrows(IllegalStateException::class.java) {
            DriverBackupJournal.recordApplied(context, applied, true)
        }
    }
}
