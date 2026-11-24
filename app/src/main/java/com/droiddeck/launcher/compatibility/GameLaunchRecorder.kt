package com.droiddeck.launcher.compatibility

import android.content.Context
import android.util.Log
import com.droiddeck.launcher.frontend.GameLaunchLink
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Records published requests, not proof that a game process started. */
internal object GameLaunchRecorder {
    private val executor = ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(64),
        { task -> Thread(task, "ai-launch-history").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy()).apply { allowCoreThreadTimeOut(true) }

    internal fun requestObservation(id: String, time: Long): GameRunHistory.Observation? {
        if (!GameLaunchLink.validId(id) || time < 0) return null
        return GameRunHistory.Observation("steam:$id", null, GameRunHistory.Outcome.UNKNOWN, time)
    }

    fun publishedSteamRequest(context: Context, id: String) {
        val observation = requestObservation(id, System.currentTimeMillis()) ?: return
        val app = context.applicationContext
        try {
            executor.execute {
                try {
                    GameRunHistory(app).use { it.record(observation) }
                } catch (e: Exception) {
                    Log.w("GameLaunchRecorder", "Could not save launch observation", e)
                }
            }
        } catch (e: java.util.concurrent.RejectedExecutionException) {
            Log.w("GameLaunchRecorder", "Launch history queue full")
        }
    }
}
