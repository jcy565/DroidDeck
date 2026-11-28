package com.droiddeck.launcher.compatibility

import android.content.Context
import java.security.MessageDigest

/** A game-scoped bookmark, not a game-scoped setting or permission to apply. */
internal object SavedOptimizationChoice {
    data class Choice(val scope: String, val proposed: String)
    fun validate(choice: Choice): Choice {
        require(when (choice.scope) {
            "steam", "desktop" -> choice.proposed in setOf("1280x720", "1600x900")
            "global" -> choice.proposed in setOf("true", "false")
            else -> false
        })
        return choice
    }

    private fun key(gameId: String): String {
        GameHistoryQuery.validateIdentity(gameId, null)
        return MessageDigest.getInstance("SHA-256").digest(gameId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun prefs(context: Context) = context.getSharedPreferences("ai_optimization_bookmarks", Context.MODE_PRIVATE)

    fun load(context: Context, gameId: String): Choice? {
        val raw = prefs(context).getString(key(gameId), null) ?: return null
        val parts = raw.split('|')
        require(parts.size == 2)
        return validate(Choice(parts[0], parts[1]))
    }

    fun save(context: Context, gameId: String, choice: Choice) {
        validate(choice)
        check(prefs(context).edit().putString(key(gameId), choice.scope + "|" + choice.proposed).commit())
    }

    fun remove(context: Context, gameId: String) {
        check(prefs(context).edit().remove(key(gameId)).commit())
    }

    fun refresh(context: Context, choice: Choice, panel: Pair<Int, Int>): DroidDeckSettingsPreview.Change {
        validate(choice)
        return if (choice.scope == "global") DroidDeckSettingsPreview.protonDxvkBridge(context, choice.proposed == "true")
        else DroidDeckSettingsPreview.outputResolution(context, choice.scope, panel, choice.proposed)
    }
}
