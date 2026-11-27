package com.droiddeck.launcher.compatibility

import android.content.Context

/** Reviewed, explicit setting changes only. Never execute from preview composition. */
internal object SettingsApplyApproval {
    data class Request internal constructor(val change: DroidDeckSettingsPreview.Change,
        val panel: Pair<Int, Int>)

    fun prepare(change: DroidDeckSettingsPreview.Change, panel: Pair<Int, Int>): Request {
        DroidDeckSettingsTransaction.validate(change, panel)
        require(DroidDeckSettingsPreview.changed(change))
        return Request(change, panel)
    }

    /** Caller must obtain confirmation and supply the current panel size.
     * Transaction revalidates revision, effective value, backup and session admission. */
    fun execute(context: Context, request: Request, currentPanel: Pair<Int, Int>,
        approved: Boolean): DroidDeckSettingsTransaction.Receipt {
        require(approved)
        if (request.change.setting == DroidDeckSettingsPreview.Setting.OUTPUT_RESOLUTION)
            require(request.panel == currentPanel) { "화면 크기가 변경되었습니다. 다시 비교해 주세요." }
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        return DroidDeckSettingsTransaction.apply(context, request.change, currentPanel, approved)
    }
}
