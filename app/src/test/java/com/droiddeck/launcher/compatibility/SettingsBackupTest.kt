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

    @Test fun reviewAllDistinguishesAllSupportedKeysAndIsReadOnly() {
        val steam = "displayResolution.steam"
        val desktop = "displayResolution.desktop"
        val bridge = "bridgeProtonDxvk"
        assertTrue(SettingsBackup.reviewAll(context).all { it.status == SettingsBackup.ReviewStatus.NO_BACKUP })
        SettingsBackup.apply(context, steam, null, "1280x720")
        SettingsBackup.apply(context, desktop, null, "1600x900")
        prefs.edit().putString(desktop, "1280x720")
            .putBoolean("ai.backup.$bridge.present", true).commit()
        val before = prefs.all
        val entries = SettingsBackup.reviewAll(context).associateBy { it.key }
        assertEquals(setOf(steam, desktop, bridge), entries.keys)
        assertEquals(SettingsBackup.ReviewStatus.READY, entries[steam]?.status)
        assertEquals(SettingsBackup.ReviewStatus.CONFLICT, entries[desktop]?.status)
        assertEquals(SettingsBackup.ReviewStatus.INVALID_BACKUP, entries[bridge]?.status)
        assertNull(entries[bridge]?.review)
        assertEquals(before, prefs.all)
    }

    @Test fun reviewAllRejectsIncorrectStoredValueType() {
        SettingsBackup.apply(context, key, null, "1280x720")
        prefs.edit().putInt(key, 720).commit()
        val entry = SettingsBackup.reviewAll(context).single { it.key == key }
        assertEquals(SettingsBackup.ReviewStatus.INVALID_BACKUP, entry.status)
        assertNull(entry.review)
    }

    @Test fun recreatedIdenticalBackupRejectsOldApproval() {
        val first = SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.restoreReviewed(context, first, true)
        val second = SettingsBackup.apply(context, key, null, "1280x720")
        assertNotEquals(first.recordId, second.recordId)
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restoreReviewed(context, first, true)
        }
        assertEquals(second, SettingsBackup.review(context, key))
        SettingsBackup.restoreReviewed(context, second, true)
        assertFalse(prefs.contains(key))
    }

    @Test fun oldBackupRemainsVisibleButCannotBeRestored() {
        prefs.edit().putString(key, "1280x720")
            .putString("ai.backup.$key.applied", "1280x720")
            .putBoolean("ai.backup.$key.present", false).commit()
        val entry = SettingsBackup.reviewAll(context).single { it.key == key }
        assertEquals(SettingsBackup.ReviewStatus.LEGACY_BACKUP, entry.status)
        val review = requireNotNull(entry.review)
        assertNull(review.recordId)
        val before = prefs.all
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restoreReviewed(context, review, true)
        }
        assertEquals(before, prefs.all)
    }

    @Test fun malformedRecordIdentityIsInvalid() {
        SettingsBackup.apply(context, key, null, "1280x720")
        prefs.edit().putString("ai.backup.$key.id", "invalid").commit()
        assertEquals(SettingsBackup.ReviewStatus.INVALID_BACKUP,
            SettingsBackup.reviewAll(context).single { it.key == key }.status)
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restore(context, key, true)
        }
    }

    @Test fun orphanedIdentityCannotBeOverwritten() {
        prefs.edit().putString("ai.backup.$key.id", java.util.UUID.randomUUID().toString()).commit()
        assertEquals(SettingsBackup.ReviewStatus.INVALID_BACKUP,
            SettingsBackup.reviewAll(context).single { it.key == key }.status)
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.apply(context, key, null, "1280x720")
        }
        assertFalse(prefs.contains(key))
    }

    @Test fun ordinaryResolutionChangesInvalidateApprovalEvenAfterReturningToAppliedValue() {
        val approved = SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        SettingsBackup.writeNormalResolution(context, "steam", "1280x720")
        val entry = SettingsBackup.reviewAll(context).single { it.key == key }
        assertEquals(SettingsBackup.ReviewStatus.CONFLICT, entry.status)
        assertTrue(requireNotNull(entry.review).invalidated)
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restoreReviewed(context, approved, true)
        }
        assertEquals("1280x720", SettingsBackup.raw(context, key))
    }

    @Test fun ordinaryBridgeChangesInvalidateApprovalEvenAfterReturningToAppliedValue() {
        val bridge = "bridgeProtonDxvk"
        val approved = SettingsBackup.apply(context, bridge, null, "true")
        SettingsBackup.writeNormalBridge(context, false)
        SettingsBackup.writeNormalBridge(context, true)
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restoreReviewed(context, approved, true)
        }
        assertTrue(requireNotNull(SettingsBackup.review(context, bridge)).invalidated)
    }

    @Test fun sameValueNormalWriteKeepsApproval() {
        val approved = SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1280x720")
        assertEquals(approved, SettingsBackup.review(context, key))
        SettingsBackup.restoreReviewed(context, approved, true)
        assertFalse(prefs.contains(key))
    }

    @Test fun archiveKeepsCurrentAndPreservesExactReview() {
        SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        val conflict = requireNotNull(SettingsBackup.review(context, key))
        SettingsBackup.keepCurrentAndArchive(context, conflict, true)
        assertEquals("1600x900", SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))
        assertEquals(conflict, SettingsBackup.archivedReview(context, key))
        assertTrue(SettingsBackup.reviewAll(context).single { it.key == key }.archiveOccupied)
        val next = SettingsBackup.apply(context, key, "1600x900", "1280x720")
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.keepCurrentAndArchive(context, next, true)
        }
        assertEquals(next, SettingsBackup.review(context, key))
        assertEquals(conflict, SettingsBackup.archivedReview(context, key))
    }

    @Test fun archiveRequiresFreshApprovalAndIdleAdmission() {
        val first = SettingsBackup.apply(context, key, null, "1280x720")
        assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.keepCurrentAndArchive(context, first, false)
        }
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.keepCurrentAndArchive(context, first, true)
        }
        val conflict = requireNotNull(SettingsBackup.review(context, key))
        requireNotNull(SessionDriverGate.shared.tryAcquire(SessionDriverGate.Kind.SESSION)).use {
            assertThrows(IllegalStateException::class.java) {
                SettingsBackup.keepCurrentAndArchive(context, conflict, true)
            }
        }
        assertNull(SettingsBackup.archivedReview(context, key))
        assertEquals(conflict, SettingsBackup.review(context, key))
    }

    @Test fun archivedBooleanRetainsTypeAndOriginalSnapshot() {
        val bridge = "bridgeProtonDxvk"
        val approved = SettingsBackup.apply(context, bridge, null, "true")
        SettingsBackup.keepCurrentAndArchive(context, approved, true)
        assertTrue(prefs.getBoolean(bridge, false))
        assertTrue(prefs.getBoolean("ai.archive.$bridge.current", false))
        SettingsBackup.writeNormalBridge(context, false)
        assertEquals(approved, SettingsBackup.archivedReview(context, bridge))
    }

    @Test fun orphanedArchivePreventsOverwrite() {
        val approved = SettingsBackup.apply(context, key, null, "1280x720")
        prefs.edit().putString("ai.archive.$key.before", "preserve").commit()
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.keepCurrentAndArchive(context, approved, true)
        }
        assertEquals("preserve", prefs.getString("ai.archive.$key.before", null))
        assertEquals(approved, SettingsBackup.review(context, key))
    }

    @Test fun aggregateReviewSeparatesArchiveFromNewPendingRecord() {
        val first = SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.keepCurrentAndArchive(context, first, true)
        val next = SettingsBackup.apply(context, key, "1280x720", "1600x900")
        val before = prefs.all
        val entry = SettingsBackup.reviewAll(context).single { it.key == key }
        assertEquals(next, entry.review)
        assertEquals(first, entry.archived)
        assertTrue(entry.archiveOccupied)
        assertEquals(before, prefs.all)
    }

    @Test fun malformedArchiveIsVisibleButNeverReturnedAsValidReview() {
        val first = SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.keepCurrentAndArchive(context, first, true)
        prefs.edit().remove("ai.archive.$key.id").commit()
        val before = prefs.all
        val entry = SettingsBackup.reviewAll(context).single { it.key == key }
        assertTrue(entry.archiveOccupied)
        assertNull(entry.archived)
        assertNull(SettingsBackup.archivedReview(context, key))
        assertEquals(before, prefs.all)
    }

    @Test fun previewCapturesRawAbsenceSeparatelyFromEffectiveValue() {
        val (raw, effective) = SettingsBackup.captureBefore(context, key) { "720p" }
        assertNull(raw)
        assertEquals("720p", effective)
        prefs.edit().putString(key, "720p").commit()
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.apply(context, key, raw, "1280x720") { true }
        }
        assertEquals("720p", SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))
    }

    @Test fun effectiveValueCheckRefusesApplyWithoutCreatingBackup() {
        val (raw, _) = SettingsBackup.captureBefore(context, key) { "720p" }
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.apply(context, key, raw, "1280x720") { false }
        }
        assertFalse(prefs.contains(key))
        assertNull(SettingsBackup.review(context, key))
    }

    @Test fun conflictArchiveRejectsApprovalAfterAnotherOrdinaryAbaChange() {
        SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        val firstReview = requireNotNull(SettingsBackup.review(context, key))
        SettingsBackup.writeNormalResolution(context, "steam", "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        val fresh = requireNotNull(SettingsBackup.review(context, key))
        assertEquals(firstReview.current, fresh.current)
        assertNotEquals(firstReview.revision, fresh.revision)
        val before = prefs.all
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.keepCurrentAndArchive(context, firstReview, true)
        }
        assertEquals(before, prefs.all)
        SettingsBackup.keepCurrentAndArchive(context, fresh, true)
        assertEquals(fresh, SettingsBackup.archivedReview(context, key))
    }

    @Test fun conflictArchiveApprovalSurvivesSameValueAndOtherScopeWrites() {
        SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        val review = requireNotNull(SettingsBackup.review(context, key))
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        SettingsBackup.writeNormalResolution(context, "desktop", "1280x720")
        assertEquals(review, SettingsBackup.review(context, key))
        SettingsBackup.keepCurrentAndArchive(context, review, true)
        assertEquals(review, SettingsBackup.archivedReview(context, key))
    }

    @Test fun malformedReviewRevisionFailsClosedWithoutMutatingSettings() {
        val approved = SettingsBackup.apply(context, key, null, "1280x720")
        prefs.edit().putInt("ai.revision.$key", 1).commit()
        val before = prefs.all
        assertEquals(SettingsBackup.ReviewStatus.INVALID_BACKUP,
            SettingsBackup.reviewAll(context).single { it.key == key }.status)
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.restoreReviewed(context, approved, true)
        }
        assertThrows(IllegalStateException::class.java) {
            SettingsBackup.keepCurrentAndArchive(context, approved, true)
        }
        assertEquals(before, prefs.all)
    }

    @Test fun malformedArchivedRevisionDoesNotHideOrOverwriteArchive() {
        val approved = SettingsBackup.apply(context, key, null, "1280x720")
        SettingsBackup.keepCurrentAndArchive(context, approved, true)
        prefs.edit().putString("ai.archive.$key.revision", "invalid").commit()
        val entry = SettingsBackup.reviewAll(context).single { it.key == key }
        assertTrue(entry.archiveOccupied)
        assertNull(entry.archived)
        assertNull(SettingsBackup.archivedReview(context, key))
    }
}
