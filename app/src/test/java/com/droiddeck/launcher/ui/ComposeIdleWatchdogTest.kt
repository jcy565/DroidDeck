package com.droiddeck.launcher.ui

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ComposeIdleWatchdogTest {
    @Test fun onlyAiGithubActionsEnablesWatchdog() {
        assertFalse(ComposeIdleWatchdog.enabled(emptyMap()))
        assertFalse(ComposeIdleWatchdog.enabled(mapOf("GITHUB_ACTIONS" to "true", "GITHUB_REF_NAME" to "main")))
        assertFalse(ComposeIdleWatchdog.enabled(mapOf("GITHUB_REF_NAME" to "ai-test")))
        assertTrue(ComposeIdleWatchdog.enabled(mapOf("GITHUB_ACTIONS" to "true", "GITHUB_REF_NAME" to "ai-test")))
    }

    @Test fun quickTestKeepsItsResultAndCancelsPendingSamples() {
        val count = AtomicInteger()
        val result = ComposeIdleWatchdog.observe("quick", listOf(60_000L), {
            count.incrementAndGet()
        }) { 42 }
        assertEquals(42, result)
        assertEquals(0, count.get())
    }

    @Test fun stalledTestEmitsBoundedStackReport() {
        val ready = CountDownLatch(1)
        var report = ""
        ComposeIdleWatchdog.observe("diagnostic-test", listOf(0L), {
            report = it
            ready.countDown()
        }) {
            assertTrue(ready.await(5, TimeUnit.SECONDS))
        }
        assertTrue(report.startsWith("[compose-idle] diagnostic-test"))
        assertTrue(report.contains("  at "))
        assertTrue(report.endsWith("[compose-idle] end\n"))
    }

    @Test fun originalFailurePropagatesUnchanged() {
        val failure = IllegalStateException("original")
        val actual = assertThrows(IllegalStateException::class.java) {
            ComposeIdleWatchdog.observe("failure", listOf(60_000L)) { throw failure }
        }
        assertSame(failure, actual)
    }
}
