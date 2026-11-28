package com.droiddeck.launcher.ui

import com.droiddeck.launcher.compatibility.OptimizationComparison
import com.droiddeck.launcher.compatibility.TrialFrameSamples
import androidx.compose.material3.OutlinedTextField
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import android.os.SystemClock
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.hud.FrameStats
import com.droiddeck.launcher.hud.GameFrameFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private class TrialDatabase(context: Context) : SQLiteOpenHelper(context, "game-trials.db", null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE trial (id INTEGER PRIMARY KEY, game TEXT NOT NULL,
            model TEXT NOT NULL, scene TEXT, gpu TEXT, driver TEXT, proton TEXT, width INTEGER, height INTEGER,
            recorded INTEGER NOT NULL, frames INTEGER NOT NULL, fps REAL NOT NULL, p95 REAL NOT NULL,
            coverage_version INTEGER NOT NULL DEFAULT 0)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion in 1..2 && newVersion == 3)
        if (oldVersion == 1) db.execSQL("ALTER TABLE trial ADD COLUMN scene TEXT")
        db.execSQL("ALTER TABLE trial ADD COLUMN coverage_version INTEGER NOT NULL DEFAULT 0")
    }

    fun record(values: ContentValues): String {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val sample = OptimizationComparison.Sample(values.getAsString("game"), values.getAsString("model"),
                values.getAsString("scene"), values.getAsInteger("width"), values.getAsInteger("height"),
                values.getAsDouble("fps"), values.getAsDouble("p95"))
            val previous = db.rawQuery("SELECT fps, p95 FROM trial WHERE game=? AND model=? AND scene=? AND width=? AND height=? AND coverage_version=1 ORDER BY id DESC LIMIT 1",
                arrayOf(sample.gameId, sample.deviceId, sample.scene, sample.outputWidth.toString(), sample.outputHeight.toString())).use {
                if (it.moveToFirst()) sample.copy(fps = it.getDouble(0), p95Ms = it.getDouble(1)) else null
            }
            db.insertOrThrow("trial", null, values)
            db.execSQL("DELETE FROM trial WHERE id <= (SELECT id FROM trial ORDER BY id DESC LIMIT 1 OFFSET 500)")
            db.setTransactionSuccessful()
            return listOfNotNull(previous?.let { "이전  " + format(it.fps, it.p95Ms) },
                "이번  " + format(sample.fps, sample.p95Ms),
                previous?.let { OptimizationComparison.describe(OptimizationComparison.compare(it, sample)) } ?: "같은 장면과 출력 해상도의 첫 검증된 측정입니다.").joinToString("\n")
        } finally { db.endTransaction() }
    }

    private fun format(fps: Double, p95: Double) = String.format(Locale.US, "%.1f FPS · P95 %.1f ms", fps, p95)
}

@Composable
internal fun GamePerformanceTrial(game: String?, activeGame: () -> String?, feed: GameFrameFeed,
                                 gpu: String, driver: String?, proton: String?) {
    if (game.isNullOrBlank()) return
    key(game) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var job by remember { mutableStateOf<Job?>(null) }
        var message by remember { mutableStateOf("") }
        var scene by remember { mutableStateOf("") }
        OutlinedTextField(scene, { scene = it.take(80) }, enabled = job?.isActive != true,
            label = { Text("비교할 장면 이름") }, singleLine = true)
        TextButton(enabled = scene.isNotBlank(), onClick = {
            val current = job
            if (current?.isActive == true) {
                current.cancel()
                message = "측정을 중단했습니다."
            } else job = scope.launch {
                try {
                    val measuredScene = scene.trim()
                    require(measuredScene.isNotBlank() && activeGame() == game)
                    require(feed.live && !feed.steamFocused && feed.pid > 0) { "게임 프레임 측정값이 필요합니다." }
                    val pid = feed.pid
                    val width = feed.outputWidth
                    val height = feed.outputHeight
                    require(width > 0 && height > 0) { "게임 출력 해상도를 확인할 수 없습니다." }
                    val ring = FloatArray(FrameStats.GRAPH_FRAMES)
                    var seen = feed.stats.graph(ring)
                    val samples = ArrayList<Float>()
                    message = "10초 측정 중 · 같은 장면을 유지하세요."
                    val start = SystemClock.elapsedRealtime()
                    while (SystemClock.elapsedRealtime() - start < 10000) {
                        delay(150)
                        require(activeGame() == game && feed.live && !feed.steamFocused && feed.pid == pid &&
                            feed.outputWidth == width && feed.outputHeight == height) { "게임 또는 출력이 바뀌어 측정을 중단했습니다." }
                        val total = feed.stats.graph(ring)
                        val count = total - seen
                        require(count in 0L..ring.size.toLong()) { "프레임 표본이 누락됐습니다." }
                        require(samples.size + count <= 20_000L) { "프레임 표본 한도를 초과했습니다." }
                        for (i in ring.size - count.toInt() until ring.size) samples.add(ring[i])
                        seen = total
                    }
                    val metrics = TrialFrameSamples.summarize(samples, SystemClock.elapsedRealtime() - start)
                    val values = ContentValues().apply {
                        put("game", game)
                        put("scene", measuredScene)
                        put("model", Build.MODEL)
                        put("gpu", gpu)
                        put("driver", driver)
                        put("proton", proton)
                        put("width", width)
                        put("height", height)
                        put("recorded", System.currentTimeMillis())
                        put("frames", metrics.frames)
                        put("fps", metrics.fps)
                        put("p95", metrics.p95Ms)
                        put("coverage_version", 1)
                    }
                    message = withContext(Dispatchers.IO) { TrialDatabase(context).use { it.record(values) } }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = e.message ?: "측정 실패" }
                finally { job = null }
            }
        }) { Text(if (job?.isActive != true) "게임 성능 10초 측정" else "측정 중단") }
        if (message.isNotEmpty()) Text(message)
    }
}
