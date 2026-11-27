package com.droiddeck.launcher.compatibility

import android.content.Context
import java.util.UUID

/** One pending backup per supported key. Stored in the same preference file
 * so the backup and setting update can be committed together. */
internal object SettingsBackup {
    data class RecoveryReview internal constructor(
        val key: String, val before: String?, val applied: String, val current: String?,
        val recordId: String?, val invalidated: Boolean = false,
        val revision: String? = null,
    ) {
        val matchesApplied: Boolean get() = !invalidated && current == applied
    }

    enum class ReviewStatus { NO_BACKUP, INVALID_BACKUP, CONFLICT, LEGACY_BACKUP, READY }

    data class ReviewEntry(
        val key: String, val status: ReviewStatus, val review: RecoveryReview?,
        val archiveOccupied: Boolean,
        val archived: RecoveryReview?,
    )

    private val recordFields = listOf("applied", "present", "before", "id", "invalidated")
    private val archiveFields = recordFields + listOf("current", "currentPresent", "revision")
    private val supportedKeys = listOf("displayResolution.steam", "displayResolution.desktop", "bridgeProtonDxvk")

    private fun prefs(context: Context) = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private fun prefix(key: String): String {
        require(key in supportedKeys)
        return "ai.backup.$key."
    }

    private fun archivePrefix(key: String): String {
        require(key in supportedKeys)
        return "ai.archive.$key."
    }

    // A revision is distinct from a backup ID: it also tracks normal writes
    // before any backup has been created. Missing means an untouched key.
    private fun revisionKey(key: String): String {
        require(key in supportedKeys)
        return "ai.revision.$key"
    }

    private fun revision(context: Context, key: String): String? {
        val all = prefs(context).all
        val field = revisionKey(key)
        if (!all.containsKey(field)) return null
        val id = all[field] as? String ?: error("잘못된 설정 변경 이력")
        val canonical = try { UUID.fromString(id).toString() }
            catch (_: IllegalArgumentException) { error("잘못된 설정 변경 이력") }
        check(id == canonical) { "잘못된 설정 변경 이력" }
        return id
    }

    data class PreviewSnapshot(val key: String, val raw: String?, val effective: String, val revision: String?)

    @Synchronized
    fun capturePreview(context: Context, key: String, effective: () -> String): PreviewSnapshot =
        PreviewSnapshot(key, raw(context, key), effective(), revision(context, key))

    private fun <T> withAdmission(action: () -> T): T {
        val lease = SessionDriverGate.shared.tryAcquire(SessionDriverGate.Kind.DRIVER_WRITE) {
            GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE)
        } ?: error("설정 변경 또는 게임 실행이 진행 중입니다.")
        return lease.use { action() }
    }

    private fun validProposed(key: String, value: String): Boolean =
        if (key == "bridgeProtonDxvk") value in setOf("true", "false")
        else value in setOf("1280x720", "1600x900")

    @Synchronized
    fun captureBefore(context: Context, key: String, effective: () -> String): Pair<String?, String> =
        raw(context, key) to effective()

    @Synchronized
    fun apply(context: Context, key: String, expectedRaw: String?, proposed: String,
        expectedPreview: PreviewSnapshot? = null,
        verifyBefore: () -> Boolean = { true }): RecoveryReview = withAdmission {
        val p = prefs(context)
        val b = prefix(key)
        require(validProposed(key, proposed))
        require(expectedRaw != proposed)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        check(recordFields.none { p.contains(b + it) }) { "기존 백업을 먼저 복구하세요." }
        val currentRevision = revision(context, key)
        check(expectedPreview == null || (expectedPreview.key == key && expectedPreview.raw == expectedRaw &&
            expectedPreview.revision == currentRevision)) { "미리보기 이후 설정이 변경되었습니다." }
        check(raw(context, key) == expectedRaw && verifyBefore()) { "설정이 변경되었습니다." }
        val recordId = UUID.randomUUID().toString()
        val newRevision = UUID.randomUUID().toString()
        val e = p.edit().putString(b + "applied", proposed)
            .putString(b + "id", recordId)
            .putString(revisionKey(key), newRevision)
            .putBoolean(b + "present", expectedRaw != null)
        if (expectedRaw != null) e.putString(b + "before", expectedRaw)
        else e.remove(b + "before")
        if (key == "bridgeProtonDxvk") e.putBoolean(key, proposed.toBooleanStrict())
        else e.putString(key, proposed)
        check(e.commit()) { "설정 저장 실패: 현재 상태를 다시 확인하세요." }
        RecoveryReview(key, expectedRaw, proposed, proposed, recordId, revision = newRevision)
    }

    /** Preserve ordinary settings behavior (apply, not commit), while retiring
     * an outstanding approval if the normal UI changes its value. */
    @Synchronized
    fun writeNormalResolution(context: Context, mode: String, value: String) {
        val key = "displayResolution.$mode"
        val p = prefs(context)
        val e = p.edit().putString(key, value)
        invalidateOnChange(p.all, key, value, e)
        e.apply()
    }

    @Synchronized
    fun writeNormalBridge(context: Context, value: Boolean) {
        val key = "bridgeProtonDxvk"
        val p = prefs(context)
        val e = p.edit().putBoolean(key, value)
        invalidateOnChange(p.all, key, value, e)
        e.apply()
    }

    private fun invalidateOnChange(all: Map<String, *>, key: String, value: Any,
        editor: android.content.SharedPreferences.Editor) {
        if (key !in supportedKeys || all[key] == value) return
        val b = prefix(key)
        editor.putString(revisionKey(key), UUID.randomUUID().toString())
        if (recordFields.any { all.containsKey(b + it) }) editor.putBoolean(b + "invalidated", true)
    }

    fun raw(context: Context, key: String): String? {
        prefix(key)
        val p = prefs(context)
        if (!p.contains(key)) return null
        return if (key == "bridgeProtonDxvk") p.getBoolean(key, false).toString()
        else p.getString(key, null)
    }

    /** Read a durable review after process recreation; malformed records fail closed. */
    @Synchronized
    fun review(context: Context, key: String): RecoveryReview? {
        return readReview(key, prefs(context).all)
    }

    /** On-demand, bounded inspection of the three known keys only. No writes. */
    @Synchronized
    fun reviewAll(context: Context): List<ReviewEntry> {
        val all = prefs(context).all
        return supportedKeys.map { key ->
            val b = prefix(key)
            val review = readReview(key, all)
            val status = when {
                review != null && !review.matchesApplied -> ReviewStatus.CONFLICT
                review != null && review.recordId == null -> ReviewStatus.LEGACY_BACKUP
                review != null -> ReviewStatus.READY
                recordFields.any { all.containsKey(b + it) } -> ReviewStatus.INVALID_BACKUP
                else -> ReviewStatus.NO_BACKUP
            }
            val archive = archivePrefix(key)
            val archiveOccupied = archiveFields.any { all.containsKey(archive + it) }
            val archived = if (archiveOccupied) readArchived(key, all) else null
            ReviewEntry(key, status, review, archiveOccupied, archived)
        }
    }

    private fun readReview(key: String, all: Map<String, *>,
        b: String = prefix(key), currentKey: String = key,
        reviewRevisionKey: String = revisionKey(key)): RecoveryReview? {
        val applied = all[b + "applied"] as? String ?: return null
        if (!validProposed(key, applied)) return null
        val present = all[b + "present"] as? Boolean ?: return null
        val before = if (present) all[b + "before"] as? String ?: return null else null
        if (!present && all.containsKey(b + "before")) return null
        if (key == "bridgeProtonDxvk" && before != null && before !in setOf("true", "false")) return null
        if (before == applied) return null
        val value = all[currentKey]
        val current = when {
            !all.containsKey(currentKey) -> null
            key == "bridgeProtonDxvk" -> (value as? Boolean ?: return null).toString()
            else -> value as? String ?: return null
        }
        val recordId = if (all.containsKey(b + "id")) {
            val id = all[b + "id"] as? String ?: return null
            val canonical = try { UUID.fromString(id).toString() } catch (_: IllegalArgumentException) { return null }
            if (canonical != id) return null
            id
        } else null
        val invalidated = if (all.containsKey(b + "invalidated")) {
            all[b + "invalidated"] as? Boolean ?: return null
        } else false
        val reviewRevision = if (all.containsKey(reviewRevisionKey)) {
            val id = all[reviewRevisionKey] as? String ?: return null
            val canonical = try { UUID.fromString(id).toString() } catch (_: IllegalArgumentException) { return null }
            if (canonical != id) return null
            id
        } else null
        return RecoveryReview(key, before, applied, current, recordId, invalidated, reviewRevision)
    }

    /** One immutable archive slot per key. Never overwrite an earlier archive.
     * Retains the exact pending record and current value without changing the setting.
     * The caller must obtain explicit approval, and invoke off the main thread. */
    @Synchronized
    fun keepCurrentAndArchive(context: Context, expected: RecoveryReview, approved: Boolean) {
        require(approved)
        withAdmission {
            val key = expected.key
            val p = prefs(context)
            val all = p.all
            val b = prefix(key)
            val archive = archivePrefix(key)
            val review = checkNotNull(readReview(key, all))
            check(review.recordId != null && review == expected) { "복구 기록이 변경되었거나 이전 형식입니다." }
            check(archiveFields.none { all.containsKey(archive + it) }) { "보관된 이전 기록이 있습니다." }
            val e = p.edit()
            for (field in recordFields) {
                when (val value = all[b + field]) {
                    null -> check(!all.containsKey(b + field))
                    is String -> e.putString(archive + field, value)
                    is Boolean -> e.putBoolean(archive + field, value)
                    else -> error("잘못된 백업 형식")
                }
                e.remove(b + field)
            }
            // Distinguish intentionally absent values from missing/corrupt archive fields.
            e.putBoolean(archive + "currentPresent", all.containsKey(key))
            when (val value = all[key]) {
                null -> check(!all.containsKey(key))
                is String -> e.putString(archive + "current", value)
                is Boolean -> e.putBoolean(archive + "current", value)
                else -> error("잘못된 현재 설정 형식")
            }
            review.revision?.let { e.putString(archive + "revision", it) }
            check(e.commit()) { "백업 보관 실패: 현재 상태를 다시 확인하세요." }
        }
    }

    /** Historical snapshot only, not eligible for automatic restoration. */
    @Synchronized
    fun archivedReview(context: Context, key: String): RecoveryReview? {
        return readArchived(key, prefs(context).all)
    }

    private fun readArchived(key: String, all: Map<String, *>): RecoveryReview? {
        val archive = archivePrefix(key)
        // Preserve legacy archives without guessing whether a missing value was absent.
        val present = all[archive + "currentPresent"] as? Boolean ?: return null
        if (present != all.containsKey(archive + "current")) return null
        return readReview(key, all, archive, archive + "current", archive + "revision")
            ?.takeIf { it.recordId != null }
    }

    @Synchronized
    fun restore(context: Context, key: String, approved: Boolean) {
        require(approved)
        withAdmission { restoreLocked(context, key, null) }
    }

    /** The reviewed record itself must still match, not just its applied value. */
    @Synchronized
    fun restoreReviewed(context: Context, expected: RecoveryReview, approved: Boolean) {
        require(approved)
        withAdmission { restoreLocked(context, expected.key, expected) }
    }

    private fun restoreLocked(context: Context, key: String, expected: RecoveryReview?) {
        val p = prefs(context)
        val b = prefix(key)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        val review = checkNotNull(review(context, key)) { "복구 기록을 확인할 수 없습니다." }
        check(review.recordId != null) { "이전 형식의 백업은 자동 복구할 수 없습니다." }
        check(expected == null || review == expected) { "복구 기록이 변경되었습니다." }
        check(review.matchesApplied) { "설정 충돌: 복구를 보류합니다." }
        val e = p.edit()
        val before = review.before
        if (before == null) e.remove(key)
        else {
            if (key == "bridgeProtonDxvk") e.putBoolean(key, before.toBooleanStrict())
            else e.putString(key, before)
        }
        recordFields.forEach { e.remove(b + it) }
        e.putString(revisionKey(key), UUID.randomUUID().toString())
        check(e.commit()) { "복구 저장 실패: 현재 상태를 다시 확인하세요." }
    }
}
