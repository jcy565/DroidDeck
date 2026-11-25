package com.droiddeck.launcher.compatibility

import android.content.Context
import com.droiddeck.launcher.session.SessionPrefs

/** Read-only first stage; no preference writes or driver installation. */
internal object DroidDeckSettingsPreview {
    enum class Setting { OUTPUT_RESOLUTION, PROTON_DXVK_BRIDGE }
    data class Change(val setting: Setting, val before: String, val proposed: String,
        val scope: String, val snapshot: SettingsBackup.PreviewSnapshot, val panel: Pair<Int, Int>?)

    fun outputResolution(context: Context, mode: String, panel: Pair<Int, Int>, proposed: String): Change {
        require(mode in setOf("steam", "desktop"))
        require(panel.first > 0 && panel.second > 0)
        require(proposed in setOf("1280x720", "1600x900"))
        val snapshot = SettingsBackup.capturePreview(context, "displayResolution.$mode") {
            SessionPrefs.resolutionChoice(context, mode, panel)
        }
        return Change(Setting.OUTPUT_RESOLUTION, snapshot.effective, proposed, mode, snapshot, panel)
    }

    fun protonDxvkBridge(context: Context, proposed: Boolean): Change {
        val snapshot = SettingsBackup.capturePreview(context, "bridgeProtonDxvk") {
            SessionPrefs.bridgeProtonDxvk(context).toString()
        }
        return Change(Setting.PROTON_DXVK_BRIDGE, snapshot.effective, proposed.toString(), "global", snapshot, null)
    }

    fun changed(change: Change): Boolean = change.before != change.proposed
}
