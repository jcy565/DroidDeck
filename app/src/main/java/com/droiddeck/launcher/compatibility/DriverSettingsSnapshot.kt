package com.droiddeck.launcher.compatibility

import android.content.Context

/** Read-only snapshot of verified driver selector preference keys.
 * Does not represent installed files, automatic driver jobs or live compositor state. */
internal object DriverSettingsSnapshot {
    private val keys = listOf("linuxDriver", "androidDriver", "gpuDriverMode",
        "linuxDriver.steam", "linuxDriver.desktop")

    data class Snapshot internal constructor(val values: Map<String, String?>)

    fun capture(context: Context): Snapshot {
        val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
        val all = prefs.all
        return Snapshot(keys.associateWith { key ->
            val value = all[key]
            require(value == null || value is String) { "Unexpected driver preference type" }
            value as String?
        })
    }

    fun changed(before: Snapshot, after: Snapshot): Set<String> {
        require(before.values.keys == keys.toSet() && after.values.keys == keys.toSet())
        return keys.filter { before.values[it] != after.values[it] }.toSet()
    }

    fun matches(context: Context, expected: Snapshot): Boolean = capture(context) == expected

    /** A malformed preference must not crash an asynchronous selection callback. */
    fun captureOrNull(context: Context): Snapshot? = try {
        capture(context)
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Revalidate the complete raw selector state and session gate before selecting.
     * This is a conservative check, not a lifecycle lock. */
    fun canCompleteSelection(context: Context, expected: Snapshot?): Boolean =
        expected != null &&
            GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE) &&
            captureOrNull(context) == expected &&
            GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE)
}
