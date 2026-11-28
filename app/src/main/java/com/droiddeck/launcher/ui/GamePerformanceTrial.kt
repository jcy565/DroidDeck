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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal class TrialDatabase(context: Context, name: String = "game-trials.db") : SQLiteOpenHelper(context, name, null, 3) {
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
        val stable = ContentValues(values)
        val sample = OptimizationComparison.Sample(requireNotNull(stable.getAsString("game")),
            requireNotNull(stable.getAsString("model")), requireNotNull(stable.getAsString("scene")),
            requireNotNull(stable.getAsInteger("width")), requireNotNull(stable.getAsInteger("height")),
            requireNotNull(stable.getAsDouble("fps")), requireNotNull(stable.getAsDouble("p95")))
        require(OptimizationComparison.compare(sample, sample).verdict == OptimizationComparison.Verdict.INCONCLUSIVE)
        require(stable.getAsInteger("coverage_version") == 1 &&
            requireNotNull(stable.getAsInteger("frames")) in 2..20_000 &&
            requireNotNull(stable.getAsLong("recorded")) > 0L)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val previous = db.rawQuery("SELECT fps, p95, gpu, driver, proton FROM trial WHERE game=? AND model=? AND scene=? AND width=? AND height=? AND coverage_version=1 ORDER BY id DESC LIMIT 1",
                arrayOf(sample.gameId, sample.deviceId, sample.scene, sample.outputWidth.toString(), sample.outputHeight.toString())).use {
                if (it.moveToFirst()) sample.copy(fps = it.getDouble(0), p95Ms = it.getDouble(1)) to
                    runtimeLabel(it.getString(2), it.getString(3), it.getString(4)) else null
            }
            db.insertOrThrow("trial", null, stable)
            db.execSQL("DELETE FROM trial WHERE id <= (SELECT id FROM trial ORDER BY id DESC LIMIT 1 OFFSET 500)")
            db.setTransactionSuccessful()
            return listOfNotNull(previous?.let { "이전  " + format(it.first.fps, it.first.p95Ms) + "\n" + it.second },
                "이번  " + format(sample.fps, sample.p95Ms) + "\n" +
                    runtimeLabel(stable.getAsString("gpu"), stable.getAsString("driver"), stable.getAsString("proton")),
                previous?.let { OptimizationComparison.describe(OptimizationComparison.compare(it.first, sample)) } ?: "같은 장면과 출력 해상도의 첫 검증된 측정입니다.").joinToString("\n")
        } finally { db.endTransaction() }
    }

    private fun runtimeLabel(gpu: String?, driver: String?, proton: String?): String {
        fun label(value: String?) = value?.takeIf { it.isNotBlank() } ?: "미확인"
        return "GPU: ${label(gpu)} · 드라이버: ${label(driver)} · Proton: ${label(proton)}"
    }

    private fun format(fps: Double, p95: Double) = String.format(Locale.US, "%.1f FPS · P95 %.1f ms", fps, p95)
}

@Composable
internal fun GamePerformanceTrial(game: String?, activeGame: () -> String?, feed: GameFrameFeed,
                                 gpu: String, driver: String?, proton: String?) {
    if (game.isNullOrBlank()) return
    key(game, feed, gpu, driver, proton) {
        val latestActiveGame by rememberUpdatedState(activeGame)
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var job by remember { mutableStateOf<Job?>(null) }
        var message by remember { mutableStateOf("") }
        var scene by remember { mutableStateOf("") }
        var saving by remember { mutableStateOf(false) }
        var cancelling by remember { mutableStateOf(false) }
        OutlinedTextField(scene, { scene = it.take(80) }, enabled = job == null,
            label = { Text("비교할 장면 이름") }, singleLine = true)
        TextButton(enabled = scene.isNotBlank() && !saving && !cancelling, onClick = {
            if (saving || cancelling) return@TextButton
            val current = job
            if (current != null) {
                cancelling = true
                message = "측정을 중단하는 중입니다."
                current.cancel()
                return@TextButton
            }
            val next = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val measuredScene = scene.trim()
                    require(measuredScene.isNotBlank() && latestActiveGame() == game)
                    require(feed.live && !feed.steamFocused && feed.pid > 0) { "게임 프레임 측정값이 필요합니다." }
                    val pid = feed.pid
                    val width = feed.outputWidth
                    val height = feed.outputHeight
                    require(width > 0 && height > 0) { "게임 출력 해상도를 확인할 수 없습니다." }
                    val ring = FloatArray(FrameStats.GRAPH_FRAMES)
                    var seen = feed.stats.graphSnapshot(ring)
                    val samples = ArrayList<Float>()
                    message = "10초 측정 중 · 같은 장면을 유지하세요."
                    val start = SystemClock.elapsedRealtime()
                    while (SystemClock.elapsedRealtime() - start < 10000) {
                        delay(150)
                        require(latestActiveGame() == game && feed.live && !feed.steamFocused && feed.pid == pid &&
                            feed.outputWidth == width && feed.outputHeight == height) { "게임 또는 출력이 바뀌어 측정을 중단했습니다." }
                        val total = feed.stats.graphSnapshot(ring)
                        val count = total.newFramesSince(seen)
                        require(samples.size + count <= 20_000L) { "프레임 표본 한도를 초과했습니다." }
                        for (i in ring.size - count until ring.size) samples.add(ring[i])
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
                    currentCoroutineContext().ensureActive()
                    saving = true
                    message = "검증된 측정값을 저장하는 중입니다."
                    message = withContext(Dispatchers.IO) {
                        TrialDatabase(context).use {
                            currentCoroutineContext().ensureActive()
                            it.record(values)
                        }
                    }
                } catch (e: CancellationException) {
                    if (!saving) message = "측정을 중단했습니다."
                    throw e
                } catch (e: Exception) { message = e.message ?: "측정 실패" }
                finally { saving = false; cancelling = false; job = null }
            }
            job = next
            next.start()
        }) { Text(when {
            saving -> "저장 중"
            cancelling -> "중단 중"
            job != null -> "측정 중단"
            else -> "게임 성능 10초 측정"
        }) }
        if (message.isNotEmpty()) Text(message)
    }
}
