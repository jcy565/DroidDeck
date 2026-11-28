package com.droiddeck.launcher.files

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class GameAgentTest {
    private val config = GameAgent.Request("속도 설정 변경", "ini", "[Game]\r\nSpeed=1\r\nOther=2\r\n")
    private val mod = """{"action":"mod","section":"Game","command":"모드: Speed=1.5","explanation":"설정 변경 제안"}"""
    private class Model(private val reply: suspend (String) -> String) : GameAgentModel {
        var closed = false
        var prompt = ""
        override suspend fun complete(prompt: String): String {
            this.prompt = prompt
            return reply(prompt)
        }
        override fun close() { closed = true }
    }

    @Test fun configProposalOnlyUsesExistingSetting() = runBlocking {
        val model = Model { mod }
        val result = GameAgent.run(config, { true }, { model }) as GameAgent.Result.Patch
        assertEquals("[Game]\r\nSpeed=1.5\r\nOther=2\r\n", result.preview.proposed)
        assertEquals(config.source, result.preview.original)
        assertTrue(model.closed)
        assertTrue(model.prompt.contains("untrusted data"))
    }

    @Test fun rejectsUnrecognizedActionsFieldsAndValues() = runBlocking {
        for (invalid in listOf(mod.replace("Speed=1.5", "Missing=2"),
            mod.replace("Speed=1.5", "Speed=true"), mod.replace("Speed=1.5", "Speed=1"),
            mod.replace("\"action\":\"mod\"", "\"action\":\"shell\""),
            mod.replace("\"section\"", "\"path\""),
            mod.replace("\"action\":\"mod\"", "\"action\":\"mod\",\"action\":\"mod\""),
            "markdown $mod", "x".repeat(8193))) {
            val model = Model { invalid }
            assertTrue(runCatching { GameAgent.run(config, { true }, { model }) }.isFailure)
            assertTrue(model.closed)
        }
    }

    @Test fun translationIsLimitedToUneditedWindowAndPreservesTokens() = runBlocking {
        val source = (1..7).joinToString("\n", "id\ttext\n") { "id$it\tHello {name}" }
        val request = GameAgent.Request("한글화", "tsv", source, mapOf("id1" to "기존 {name}"))
        val output = """{"action":"translate","explanation":"번역","row.id2":"안녕 {name}"}"""
        val model = Model { output }
        val result = GameAgent.run(request, { true }, { model }) as GameAgent.Result.Translation
        assertEquals(mapOf("id1" to "기존 {name}", "id2" to "안녕 {name}"), result.edits)
        assertEquals(2, result.next)
        assertFalse(model.prompt.contains("row.id1"))
        assertTrue(model.prompt.contains("row.id6"))
        assertFalse(model.prompt.contains("row.id7"))
        for (invalid in listOf(output.replace("row.id2", "row.id7"),
            output.replace("row.id2", "row.id1"), output.replace("안녕 {name}", "안녕"))) {
            assertTrue(runCatching { GameAgent.run(request, { true }, { Model { invalid } }) }.isFailure)
        }
    }

    @Test fun translationWrapsPastEditedRowsAndRetriesPartialReplies() = runBlocking {
        val source = (1..8).joinToString("\n", "id\ttext\n") { "id$it\tHello {name}" }
        val existing = (3..7).associate { "id$it" to "기존 {name}" }
        val request = GameAgent.Request("한글화", "tsv", source, existing, start = 2)
        val model = Model { """{"action":"translate","explanation":"번역","row.id8":"안녕 {name}","row.id1":"Hello {name}"}""" }
        val result = GameAgent.run(request, { true }, { model }) as GameAgent.Result.Translation
        assertEquals(existing + ("id8" to "안녕 {name}"), result.edits)
        assertEquals(0, result.next)
        assertTrue(model.prompt.contains("row.id8"))
        assertTrue(model.prompt.contains("row.id1"))
        assertTrue(model.prompt.contains("row.id2"))
        assertFalse(model.prompt.contains("row.id3"))
        val resumed = request.copy(existing = result.edits, start = result.next)
        val last = GameAgent.run(resumed, { true }, { Model {
            """{"action":"translate","explanation":"번역","row.id1":"안녕 {name}","row.id2":"안녕 {name}"}"""
        } }) as GameAgent.Result.Translation
        assertEquals(8, last.next)
        assertEquals(8, last.edits.size)
        assertTrue(runCatching { GameAgent.externalPrompt(resumed.copy(existing = last.edits), java.util.UUID.randomUUID().toString()) }.isFailure)
    }

    @Test fun logAdviceCannotProducePatch() = runBlocking {
        val request = GameAgent.Request("원인 분석", "log", "reason LOW_MEMORY")
        val advice = GameAgent.run(request, { true }, { Model {
            """{"action":"advice","explanation":"메모리 부족 가능성"}"""
        } }) as GameAgent.Result.Advice
        assertTrue(advice.evidence.contains("LOW_MEMORY"))
        assertTrue(runCatching { GameAgent.run(request, { true }, { Model { mod } }) }.isFailure)
    }

    @Test fun budgetTimeoutAndCancellationCloseModel() = runBlocking {
        assertTrue(runCatching { GameAgent.run(config, { false }, { error("must not start") }) }.isFailure)
        var allowed = true
        val denied = Model { allowed = false; mod }
        assertTrue(runCatching { GameAgent.run(config, { allowed }, { denied }) }.isFailure)
        assertTrue(denied.closed)
        val slow = Model { delay(1000); mod }
        assertTrue(runCatching { GameAgent.run(config, { true }, { slow }, 5) }.isFailure)
        assertTrue(slow.closed)
        val entered = CompletableDeferred<Unit>()
        val cancelled = Model { entered.complete(Unit); awaitCancellation() }
        val job = launch { GameAgent.run(config, { true }, { cancelled }) }
        entered.await()
        job.cancelAndJoin()
        assertTrue(cancelled.closed)
        assertTrue(job.isCancelled)
    }

    @Test fun cancellationDuringModelVerificationPreventsOpeningModel() = runBlocking {
        val verifying = CompletableDeferred<Unit>()
        val resume = java.util.concurrent.CountDownLatch(1)
        val model = Model { mod }
        var opened = false
        val job = launch(Dispatchers.IO) {
            GameAgent.run(config, { true }, { allowed ->
                verifying.complete(Unit)
                check(resume.await(10, java.util.concurrent.TimeUnit.SECONDS))
                check(allowed())
                opened = true
                model
            })
        }
        try {
            withTimeout(5000) { verifying.await() }
            job.cancel()
        } finally {
            resume.countDown()
            job.join()
        }
        assertFalse(opened)
        assertEquals("", model.prompt)
    }

    @Test fun budgetChangeWhileOpeningClosesModelBeforeInference() = runBlocking {
        var idle = true
        val model = Model { mod }
        val failure = runCatching {
            GameAgent.run(config, { idle }, { allowed ->
                check(allowed())
                idle = false
                model
            })
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(model.closed)
        assertEquals("", model.prompt)
    }
}
