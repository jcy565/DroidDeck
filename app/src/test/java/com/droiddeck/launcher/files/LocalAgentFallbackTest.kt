package com.droiddeck.launcher.files

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocalAgentFallbackTest {
    private val request = GameAgent.Request("원인 분석", "log", "reason LOW_MEMORY")
    private val answer = GameAgent.Result.Advice("확인 필요", "LOW_MEMORY")

    @Test fun successfulLocalResultDoesNotCreateExternalRequest() = runBlocking {
        val result = LocalAgentFallback.run("/game/a.log", request, { true },
            { error("external gate must not run") }, { true }) { answer }
        assertEquals(LocalAgentFallback.Outcome.Local(answer), result)
    }

    @Test fun declineTimeoutAndUnavailablePrepareSameBoundRequest() = runBlocking {
        val declined = LocalAgentFallback.run("/game/a.log", request, { true }, { true }, { true }) {
            throw GameAgent.NeedsExternalHelp()
        } as LocalAgentFallback.Outcome.External
        assertEquals(LocalAgentFallback.Reason.LOCAL_DECLINED, declined.reason)
        assertEquals(request, declined.pending.request)
        val timed = LocalAgentFallback.run("/game/a.log", request, { true }, { true }, { true }) {
            withTimeout(5) { delay(1000); answer }
        } as LocalAgentFallback.Outcome.External
        assertEquals(LocalAgentFallback.Reason.TIMEOUT, timed.reason)
        val unavailable = LocalAgentFallback.run("/game/a.log", request, { false }, { true }, { true }) {
            error("local must not run")
        } as LocalAgentFallback.Outcome.External
        assertEquals(LocalAgentFallback.Reason.LOCAL_UNAVAILABLE, unavailable.reason)
    }

    @Test fun cancellationChangedSourceAndBudgetDoNotPrepareExchange() = runBlocking {
        assertTrue(runCatching { LocalAgentFallback.run("/game/a.log", request, { true }, { true }, { false }) {
            error("local failed")
        } }.isFailure)
        assertTrue(runCatching { LocalAgentFallback.run("/game/a.log", request, { false }, { false }, { true }) {
            error("local must not run")
        } }.isFailure)
        val entered = CompletableDeferred<Unit>()
        val job = launch {
            LocalAgentFallback.run("/game/a.log", request, { true },
                { error("cancelled job must not fall back") }, { true }) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }

    @Test fun localModelCanDeclineWithoutProducingAdviceOrPatch() = runBlocking {
        var closed = false
        val model = object : GameAgentModel {
            override suspend fun complete(prompt: String): String {
                assertTrue(prompt.contains("Local model only"))
                return """{"action":"defer","explanation":"추가 지원 필요"}"""
            }
            override fun close() { closed = true }
        }
        assertTrue(runCatching { GameAgent.run(request, { true }, { model }) }.exceptionOrNull()
            is GameAgent.NeedsExternalHelp)
        assertTrue(closed)
    }
}
