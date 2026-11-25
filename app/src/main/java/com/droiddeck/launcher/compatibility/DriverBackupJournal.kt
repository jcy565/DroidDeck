package com.droiddeck.launcher.compatibility

import android.content.Context
import org.json.JSONObject

/** One pending baseline. No driver writes or automatic restoration. */
internal object DriverBackupJournal {
    private const val KEY = "baseline"
    private const val APPLIED = "applied"
    private fun prefs(context: Context) =
        context.getSharedPreferences("ai_driver_backup", Context.MODE_PRIVATE)

    @Synchronized fun save(context: Context, expected: DriverSettingsSnapshot.Snapshot,
        approved: Boolean) {
        require(approved)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        val p = prefs(context)
        check(!p.contains(KEY) && !p.contains(APPLIED)) { "Existing driver backup requires review" }
        check(DriverSettingsSnapshot.matches(context, expected)) { "Driver settings changed" }
        val json = JSONObject()
        expected.values.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        check(p.edit().putString(KEY, json.toString()).commit()) { "Backup persistence failed" }
    }

    @Synchronized fun recordApplied(context: Context, expected: DriverSettingsSnapshot.Snapshot,
        approved: Boolean) {
        require(approved)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        val baseline = checkNotNull(load(context))
        val p = prefs(context)
        check(!p.contains(APPLIED)) { "Applied snapshot already recorded" }
        check(DriverSettingsSnapshot.changed(baseline, expected).isNotEmpty())
        check(DriverSettingsSnapshot.matches(context, expected)) { "Driver settings changed" }
        val json = JSONObject()
        expected.values.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        check(p.edit().putString(APPLIED, json.toString()).commit()) { "Applied snapshot persistence failed" }
    }

    @Synchronized fun loadApplied(context: Context): DriverSettingsSnapshot.Snapshot? =
        read(context, APPLIED)

    /** Read-only recovery eligibility; never writes driver settings. */
    @Synchronized fun canReviewRestore(context: Context, applied: DriverSettingsSnapshot.Snapshot): Boolean {
        if (!GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE)) return false
        return try {
            val baseline = load(context) ?: return false
            loadApplied(context) == applied &&
                DriverSettingsSnapshot.changed(baseline, applied).isNotEmpty() &&
                DriverSettingsSnapshot.matches(context, applied)
        } catch (_: org.json.JSONException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: ClassCastException) {
            false
        }
    }

    @Synchronized fun load(context: Context): DriverSettingsSnapshot.Snapshot? {
        return read(context, KEY)
    }

    private fun read(context: Context, key: String): DriverSettingsSnapshot.Snapshot? {
        val raw = prefs(context).getString(key, null) ?: return null
        val json = JSONObject(raw)
        val keys = DriverSettingsSnapshot.capture(context).values.keys
        require(json.keys().asSequence().toSet() == keys)
        return DriverSettingsSnapshot.Snapshot(keys.associateWith { key ->
            if (json.isNull(key)) null else json.get(key) as String
        })
    }
}
