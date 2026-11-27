package com.droiddeck.launcher.compatibility

import android.content.Context
import com.droiddeck.launcher.session.SessionPrefs

internal object DroidDeckSettingsTransaction {
    data class Receipt internal constructor(val change: DroidDeckSettingsPreview.Change,
        val panel: Pair<Int, Int>?, val recovery: SettingsBackup.RecoveryReview)

    private fun current(context: Context, change: DroidDeckSettingsPreview.Change,
        panel: Pair<Int, Int>?): String = when (change.setting) {
        DroidDeckSettingsPreview.Setting.OUTPUT_RESOLUTION ->
            SessionPrefs.resolutionChoice(context, change.scope, requireNotNull(panel))
        DroidDeckSettingsPreview.Setting.PROTON_DXVK_BRIDGE ->
            SessionPrefs.bridgeProtonDxvk(context).toString()
    }

    private fun validate(change: DroidDeckSettingsPreview.Change, panel: Pair<Int, Int>?) {
        require(change.before == change.snapshot.effective)
        when (change.setting) {
            DroidDeckSettingsPreview.Setting.OUTPUT_RESOLUTION -> {
                require(change.scope in setOf("steam", "desktop"))
                require(panel != null && panel.first > 0 && panel.second > 0 && change.panel == panel)
                require(change.proposed in setOf("1280x720", "1600x900"))
            }
            DroidDeckSettingsPreview.Setting.PROTON_DXVK_BRIDGE -> {
                require(change.scope == "global" && change.panel == null)
                require(change.before in setOf("true", "false") && change.proposed in setOf("true", "false"))
            }
        }
    }

    private fun key(change: DroidDeckSettingsPreview.Change): String = when (change.setting) {
        DroidDeckSettingsPreview.Setting.OUTPUT_RESOLUTION -> "displayResolution.${change.scope}"
        DroidDeckSettingsPreview.Setting.PROTON_DXVK_BRIDGE -> "bridgeProtonDxvk"
    }

    /** Caller must obtain explicit approval and invoke off the main thread.
     * Other settings writers and session start may still race.
     */
    @Synchronized
    fun apply(context: Context, change: DroidDeckSettingsPreview.Change,
        panel: Pair<Int, Int>?, approved: Boolean): Receipt {
        require(approved)
        validate(change, panel)
        require(change.before != change.proposed)
        val key = key(change)
        val recovery = SettingsBackup.apply(context, key, change.snapshot.raw, change.proposed, change.snapshot) {
            current(context, change, panel) == change.before
        }
        check(current(context, change, panel) == change.proposed) { "적용 상태를 확인할 수 없습니다." }
        return Receipt(change, panel, recovery)
    }

    @Synchronized
    fun restore(context: Context, receipt: Receipt, approved: Boolean) {
        require(approved)
        val change = receipt.change
        validate(change, receipt.panel)
        check(current(context, change, receipt.panel) == change.proposed) { "현재 설정이 달라 복구를 보류합니다." }
        check(receipt.recovery.key == key(change))
        SettingsBackup.restoreReviewed(context, receipt.recovery, approved)
    }
}
