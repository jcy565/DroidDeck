package com.droiddeck.launcher.compatibility

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

/** Local observations only; no network, game file changes or inferred compatibility verdicts. */
internal class GameRunHistory(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "ai_game_run_history.db", null, 1) {

    enum class Outcome { STARTED, PLAYABLE, BLACK_SCREEN, CRASH, UNKNOWN }

    data class Observation(
        val gameId: String,
        val gameVersion: String?,
        val outcome: Outcome,
        val recordedAt: Long,
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE runs (id INTEGER PRIMARY KEY, game_id TEXT NOT NULL, game_version TEXT, outcome TEXT NOT NULL, recorded_at INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX runs_game ON runs(game_id, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported history migration: $oldVersion to $newVersion")
    }

    fun record(observation: Observation) {
        require(observation.gameId.isNotBlank() && observation.gameId.length <= 256)
        require(observation.gameVersion == null || observation.gameVersion.length <= 128)
        require(observation.recordedAt >= 0)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("game_id", observation.gameId)
                put("game_version", observation.gameVersion)
                put("outcome", observation.outcome.name)
                put("recorded_at", observation.recordedAt)
            }
            db.insertOrThrow("runs", null, values)
            db.execSQL("DELETE FROM runs WHERE id NOT IN (SELECT id FROM runs ORDER BY id DESC LIMIT 500)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun recent(gameId: String, limit: Int = 20, gameVersion: String? = null): List<Observation> {
        val selection = GameHistoryQuery.recent(gameId, limit, gameVersion)
        return readableDatabase.query("runs", arrayOf("game_id", "game_version", "outcome", "recorded_at"),
            selection.where, selection.args.toTypedArray(), null, null, "id DESC", selection.limit.toString()).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Observation(cursor.getString(0),
                    if (cursor.isNull(1)) null else cursor.getString(1),
                    Outcome.valueOf(cursor.getString(2)), cursor.getLong(3)))
            }
        }
    }
}
