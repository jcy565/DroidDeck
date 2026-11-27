package com.droiddeck.launcher.ui

import android.content.ContentValues
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class TrialDatabaseTest {
    @Test fun savedMeasurementSurvivesCloseAndIsUsedForTheNextComparison() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("game-trials.db")
        val values = ContentValues().apply {
            put("game", "steam:42")
            put("model", "device")
            put("scene", "intro")
            put("width", 1280)
            put("height", 720)
            put("recorded", 1L)
            put("frames", 300)
            put("fps", 30.0)
            put("p95", 40.0)
        }
        val first = TrialDatabase(context).use { it.record(values) }
        assertTrue(first.contains("첫 측정"))
        values.put("fps", 40.0)
        val second = TrialDatabase(context).use { it.record(values) }
        assertTrue(second.contains("이전  30.0 FPS"))
        assertTrue(second.contains("이번  40.0 FPS"))
    }
}
