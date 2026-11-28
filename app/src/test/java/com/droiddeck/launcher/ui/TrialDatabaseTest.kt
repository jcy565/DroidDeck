package com.droiddeck.launcher.ui

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TrialDatabaseTest {
    private lateinit var context: Context
    private val name = "trial-regression.db"

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(name)
    }

    @After fun cleanup() {
        context.deleteDatabase(name)
    }

    private fun values(fps: Double = 40.0, driver: String = "Turnip A") = ContentValues().apply {
        put("game", "steam:1")
        put("model", "device")
        put("scene", "menu")
        put("width", 1280)
        put("height", 720)
        put("recorded", 1L)
        put("frames", 400)
        put("fps", fps)
        put("p95", 25.0)
        put("gpu", "Adreno")
        put("driver", driver)
        put("proton", "GE A")
        put("coverage_version", 1)
    }

    @Test fun realHelperMigratesBothLegacyVersionsAndPreservesRecords() {
        for (version in 1..2) {
            context.deleteDatabase(name)
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                val sceneColumn = if (version == 2) "scene TEXT," else ""
                db.execSQL("""CREATE TABLE trial (id INTEGER PRIMARY KEY, game TEXT NOT NULL,
                    model TEXT NOT NULL, $sceneColumn gpu TEXT, driver TEXT, proton TEXT, width INTEGER, height INTEGER,
                    recorded INTEGER NOT NULL, frames INTEGER NOT NULL, fps REAL NOT NULL, p95 REAL NOT NULL)""")
                val legacy = values(900.0).apply {
                    remove("coverage_version")
                    if (version == 1) remove("scene")
                }
                db.insertOrThrow("trial", null, legacy)
                db.version = version
            }
            TrialDatabase(context, name).use { history ->
                assertTrue(history.record(values()).contains("첫 검증된 측정"))
                val report = history.record(values(60.0, "Turnip B"))
                assertTrue(report.contains("40.0 FPS"))
                assertTrue(report.contains("60.0 FPS"))
                assertTrue(report.contains("Turnip A"))
                assertTrue(report.contains("Turnip B"))
                assertFalse(report.contains("900.0 FPS"))
                assertEquals(3, history.writableDatabase.version)
                history.writableDatabase.rawQuery("SELECT coverage_version FROM trial ORDER BY id", null).use {
                    val versions = mutableListOf<Int>()
                    while (it.moveToNext()) versions.add(it.getInt(0))
                    assertEquals(listOf(0, 1, 1), versions)
                }
            }
        }
    }

    @Test fun invalidRecordCannotBecomeComparisonBaseline() {
        TrialDatabase(context, name).use { history ->
            history.record(values())
            val invalid = listOf(
                values().apply { put("coverage_version", 0) },
                values().apply { put("frames", 1) },
                values().apply { put("frames", 20_001) },
                values().apply { put("recorded", 0L) },
                values().apply { put("width", 0) },
                values().apply { put("scene", " ") },
                values().apply { put("p95", Double.NaN) },
                values().apply { remove("fps") },
                values(0.0)
            )
            for (bad in invalid) assertThrows(IllegalArgumentException::class.java) { history.record(bad) }
            history.writableDatabase.rawQuery("SELECT COUNT(*) FROM trial", null).use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            assertTrue(history.record(values(60.0)).contains("40.0 FPS"))
        }
    }
}
