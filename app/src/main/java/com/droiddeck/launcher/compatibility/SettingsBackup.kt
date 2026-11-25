package com.droiddeck.launcher.compatibility

import android.content.Context

/** One pending backup per supported key. Stored in the same preference file
 * so the backup and setting update can be committed together. */
internal object SettingsBackup {
    private fun prefs(context: Context) = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    private fun prefix(key: String): String {
        require(key in setOf("displayResolution.steam", "displayResolution.desktop", "bridgeProtonDxvk"))
        return "ai.backup.$key."
    }

    @Synchronized
    fun apply(context: Context, key: String, expectedRaw: String?, proposed: String) {
        val p = prefs(context)
        val b = prefix(key)
        require(if (key == "bridgeProtonDxvk") proposed in setOf("true", "false")
            else proposed in setOf("1280x720", "1600x900"))
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        check(!p.contains(b + "applied")) { "기존 백업을 먼저 복구하세요." }
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

    @Synchronized
    fun restore(context: Context, key: String, approved: Boolean) {
        require(approved)
        val p = prefs(context)
        val b = prefix(key)
        check(GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE))
        val applied = checkNotNull(p.getString(b + "applied", null))
        check(raw(context, key) == applied) { "설정 충돌: 복구를 보류합니다." }
        check(p.contains(b + "present"))
        val e = p.edit()
        if (!p.getBoolean(b + "present", false)) e.remove(key)
        else {
            val before = checkNotNull(p.getString(b + "before", null))
            if (key == "bridgeProtonDxvk") e.putBoolean(key, before.toBooleanStrict())
            else e.putString(key, before)
        }
        e.remove(b + "applied").remove(b + "present").remove(b + "before")
        check(e.commit()) { "복구 저장 실패: 현재 상태를 다시 확인하세요." }
    }
}
