package com.droiddeck.launcher.files

import android.content.Context
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Experimental MediaPipe adapter. The standard APK does not include this source set. */
internal object LocalAgentRuntime {
    const val enabled = true
    private const val CONTEXT_TOKENS = 4096
    private const val OUTPUT_RESERVE = 1024
    private val occupied = AtomicBoolean(false)

    fun open(context: Context, file: File, allowed: () -> Boolean): GameAgentModel {
        check(allowed()) { "AI 작업을 시작하기 위한 메모리가 부족하거나 게임이 실행 중입니다." }
        check(occupied.compareAndSet(false, true)) { "이전 AI 모델이 아직 종료되지 않았습니다." }
        var engine: LlmInference? = null
        try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(CONTEXT_TOKENS)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            val created = LlmInference.createFromOptions(context.applicationContext, options)
            engine = created
            val session = LlmInferenceSession.createFromOptions(created,
                LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(1).build())
            return Model(created, session, allowed)
        } catch (failure: Throwable) {
            val closed = runCatching { engine?.close() }.isSuccess
            if (closed) occupied.set(false)
            if (failure is LinkageError) throw IllegalStateException("실험용 추론 라이브러리를 불러오지 못했습니다.", failure)
            throw failure
        }
    }

    private class Model(private val engine: LlmInference, private val session: LlmInferenceSession,
        private val allowed: () -> Boolean) : GameAgentModel {
        private var future: ListenableFuture<String>? = null
        private val closing = AtomicBoolean(false)

        override suspend fun complete(prompt: String): String {
            check(!closing.get() && future == null && allowed())
            require(session.sizeInTokens(prompt) <= CONTEXT_TOKENS - OUTPUT_RESERVE) {
                "모델 입력이 너무 깁니다. 더 작은 파일 또는 번역 범위를 사용하세요."
            }
            session.addQueryChunk(prompt)
            check(allowed())
            val active = session.generateResponseAsync()
            future = active
            while (!active.isDone) {
                check(allowed()) { "게임 실행 또는 메모리 상태가 변경되어 AI 작업을 중단합니다." }
                delay(200)
            }
            return active.get()
        }

        override fun close() {
            if (!closing.compareAndSet(false, true)) return
            val active = future
            if (active != null && !active.isDone) {
                // Native session must not be destroyed while its inference is active.
                // Keep the lease until completion; cancellation is not a hard process kill.
                runCatching { session.cancelGenerateResponseAsync() }
                val cleanup = Executors.newSingleThreadExecutor()
                active.addListener({
                    try { release() } finally { cleanup.shutdown() }
                }, cleanup)
            } else {
                release()
            }
        }

        private fun release() {
            val sessionClosed = runCatching { session.close() }.isSuccess
            val engineClosed = sessionClosed && runCatching { engine.close() }.isSuccess
            if (engineClosed) occupied.set(false)
        }
    }
}
