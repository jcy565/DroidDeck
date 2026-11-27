package com.droiddeck.launcher.files

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TranslationEngineTest {
    private val source = "id\ttext\na\tHello {name}\nb\tBye\nc\tStart"
    private class Provider(val action: suspend (String) -> String) : FileTranslationProvider {
        var calls = 0
        var closed = false
        override suspend fun translate(text: String): String {
            calls++
            return action(text)
        }
        override fun close() { closed = true }
    }

    @Test fun limitedWindowPreservesExistingEditsAndRejectsLostTokens() = runBlocking {
        val provider = Provider { "안녕" }
        val existing = mapOf("b" to "기존")
        val result = TranslationEngine.run(source, existing, window = 2,
            allowed = { true }, providerFactory = { provider })
        assertEquals(TranslationEngine.Stop.WINDOW_COMPLETE, result.stop)
        assertEquals(2, result.nextIndex)
        assertEquals(existing, result.edits)
        assertEquals(1, result.rejected)
        assertEquals(1, result.skipped)
        assertEquals(1, provider.calls)
        assertTrue(provider.closed)
    }

    @Test fun budgetStopsBeforeProviderAndAfterInFlightResult() = runBlocking {
        val blocked = TranslationEngine.run(source, emptyMap(), allowed = { false },
            providerFactory = { error("must not initialize") })
        assertEquals(TranslationEngine.Stop.BUDGET_DENIED, blocked.stop)
        var allowed = true
        val provider = Provider { allowed = false; "안녕 {name}" }
        val stopped = TranslationEngine.run(source, emptyMap(), allowed = { allowed },
            providerFactory = { provider })
        assertEquals(TranslationEngine.Stop.BUDGET_DENIED, stopped.stop)
        assertEquals(0, stopped.nextIndex)
        assertTrue(stopped.edits.isEmpty())
        assertTrue(provider.closed)
    }

    @Test fun providerFailureRetainsPriorValidatedDraftAndTimeoutCloses() = runBlocking {
        val failing = Provider { if (it.startsWith("Hello")) "안녕 {name}" else error("fixture") }
        val result = TranslationEngine.run(source, emptyMap(), allowed = { true },
            providerFactory = { failing })
        assertEquals(TranslationEngine.Stop.PROVIDER_FAILURE, result.stop)
        assertEquals(mapOf("a" to "안녕 {name}"), result.edits)
        assertEquals(1, result.nextIndex)
        assertTrue(failing.closed)
        val slow = Provider { delay(1000); "안녕" }
        val timed = TranslationEngine.run(source, emptyMap(), timeoutMillis = 5,
            allowed = { true }, providerFactory = { slow })
        assertEquals(TranslationEngine.Stop.PROVIDER_TIMEOUT, timed.stop)
        assertEquals(0, timed.nextIndex)
        assertTrue(slow.closed)
    }

    @Test fun cancellationPropagatesAndReleasesProvider() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val provider = Provider { entered.complete(Unit); awaitCancellation() }
        val job = launch {
            TranslationEngine.run(source, emptyMap(), allowed = { true }, providerFactory = { provider })
        }
        entered.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertTrue(provider.closed)
    }
}
