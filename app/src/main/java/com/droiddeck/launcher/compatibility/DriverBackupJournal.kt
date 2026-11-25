package com.droiddeck.launcher.compatibility

import android.content.Context
import org.json.JSONObject

/** One pending baseline. No driver writes or automatic restoration. */
internal object DriverBackupJournal {
    private const val KEY = "baseline"
    private fun prefs(context: Context) =
        context.getSharedPreferences("ai_driver_backup", Context.MODE_PRIVATE)

    @Synchronized fun save(context: Context, expected: DriverSettingsSnapshot.Snapshot,
        approved: Boolean) {
        require(approved)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        val p = prefs(context)
        check(!p.contains(KEY)) { "Existing driver backup requires review" }
        check(DriverSettingsSnapshot.matches(context, expected)) { "Driver settings changed" }
        val json = JSONObject()
        expected.values.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        check(p.edit().putString(KEY, json.toString()).commit()) { "Backup persistence failed" }
    }

    @Synchronized fun load(context: Context): DriverSettingsSnapshot.Snapshot? {
        val raw = prefs(context).getString(KEY, null) ?: return null
        val json = JSONObject(raw)
        val keys = DriverSettingsSnapshot.capture(context).values.keys
        require(json.keys().asSequence().toSet() == keys)
        return DriverSettingsSnapshot.Snapshot(keys.associateWith { key ->
            if (json.isNull(key)) null else json.get(key) as String
        })
    }
}
