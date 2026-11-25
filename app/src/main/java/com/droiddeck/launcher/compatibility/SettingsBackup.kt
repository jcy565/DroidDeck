package com.droiddeck.launcher.compatibility

import android.content.Context

/** One pending backup per supported key. Stored in the same preference file
 * so the backup and setting update can be committed together. */
internal object SettingsBackup {
    data class RecoveryReview internal constructor(
        val key: String, val before: String?, val applied: String, val current: String?,
    ) {
        val matchesApplied: Boolean get() = current == applied
    }

    enum class ReviewStatus { NO_BACKUP, INVALID_BACKUP, CONFLICT, READY }

    data class ReviewEntry(
        val key: String, val status: ReviewStatus, val review: RecoveryReview?,
    )

    private val supportedKeys = listOf("displayResolution.steam", "displayResolution.desktop", "bridgeProtonDxvk")

    private fun prefs(context: Context) = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private fun prefix(key: String): String {
        require(key in supportedKeys)
        return "ai.backup.$key."
    }

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
    fun apply(context: Context, key: String, expectedRaw: String?, proposed: String) = withAdmission {
        val p = prefs(context)
        val b = prefix(key)
        require(validProposed(key, proposed))
        require(expectedRaw != proposed)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        check(listOf("applied", "present", "before").none { p.contains(b + it) }) { "기존 백업을 먼저 복구하세요." }
        check(raw(context, key) == expectedRaw) { "설정이 변경되었습니다." }
        val e = p.edit().putString(b + "applied", proposed)
            .putBoolean(b + "present", expectedRaw != null)
        if (expectedRaw != null) e.putString(b + "before", expectedRaw)
        else e.remove(b + "before")
        if (key == "bridgeProtonDxvk") e.putBoolean(key, proposed.toBooleanStrict())
        else e.putString(key, proposed)
        check(e.commit()) { "설정 저장 실패: 현재 상태를 다시 확인하세요." }
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
                review != null && review.matchesApplied -> ReviewStatus.READY
                review != null -> ReviewStatus.CONFLICT
                listOf("applied", "present", "before").any { all.containsKey(b + it) } -> ReviewStatus.INVALID_BACKUP
                else -> ReviewStatus.NO_BACKUP
            }
            ReviewEntry(key, status, review)
        }
    }

    private fun readReview(key: String, all: Map<String, *>): RecoveryReview? {
        val b = prefix(key)
        val applied = all[b + "applied"] as? String ?: return null
        if (!validProposed(key, applied)) return null
        val present = all[b + "present"] as? Boolean ?: return null
        val before = if (present) all[b + "before"] as? String ?: return null else null
        if (!present && all.containsKey(b + "before")) return null
        if (key == "bridgeProtonDxvk" && before != null && before !in setOf("true", "false")) return null
        if (before == applied) return null
        val value = all[key]
        val current = when {
            !all.containsKey(key) -> null
            key == "bridgeProtonDxvk" -> (value as? Boolean ?: return null).toString()
            else -> value as? String ?: return null
        }
        return RecoveryReview(key, before, applied, current)
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
        check(expected == null || review == expected) { "복구 기록이 변경되었습니다." }
        check(review.matchesApplied) { "설정 충돌: 복구를 보류합니다." }
        val e = p.edit()
        val before = review.before
        if (before == null) e.remove(key)
        else {
            if (key == "bridgeProtonDxvk") e.putBoolean(key, before.toBooleanStrict())
            else e.putString(key, before)
        }
        e.remove(b + "applied").remove(b + "present").remove(b + "before")
        check(e.commit()) { "복구 저장 실패: 현재 상태를 다시 확인하세요." }
    }
}
