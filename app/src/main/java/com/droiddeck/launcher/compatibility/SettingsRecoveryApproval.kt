package com.droiddeck.launcher.compatibility

import android.content.Context

/** A single reviewed action, never queued or executed by inspection alone. */
internal object SettingsRecoveryApproval {
    data class Request internal constructor(
        val action: SettingsBackup.ReviewAction,
        val reviewed: SettingsBackup.RecoveryReview,
    ) {
        val targetRaw: String?
            get() = when (action) {
                SettingsBackup.ReviewAction.RESTORE -> reviewed.before
                SettingsBackup.ReviewAction.KEEP_CURRENT_AND_ARCHIVE -> reviewed.current
            }
    }

    fun prepare(entry: SettingsBackup.ReviewEntry, action: SettingsBackup.ReviewAction): Request {
        require(action in entry.availableActions)
        val record = requireNotNull(entry.review)
        require(entry.key == record.key)
        return Request(action, record)
    }

    /** Explicitly confirmed caller only, off the main thread. The backend repeats
     * admission and exact-record checks under its synchronized mutation path.
     * Do not wire to a write-enabled UI before Android validation. */
    fun execute(context: Context, request: Request, approved: Boolean) {
        require(approved)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        val entry = SettingsBackup.reviewAll(context).single { it.key == request.reviewed.key }
        check(entry.review == request.reviewed && request.action in entry.availableActions) {
            "복구 기록 또는 가능한 작업이 변경되었습니다. 다시 조회해 주세요."
        }
        when (request.action) {
            SettingsBackup.ReviewAction.RESTORE ->
                SettingsBackup.restoreReviewed(context, request.reviewed, approved)
            SettingsBackup.ReviewAction.KEEP_CURRENT_AND_ARCHIVE ->
                SettingsBackup.keepCurrentAndArchive(context, request.reviewed, approved)
        }
    }
}
