package com.droiddeck.launcher.ui

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Diagnostic only: capture blocked test-worker stacks before Compose's own
 * timeout. No changes to clocks, idling policy, assertions or test results. */
internal object ComposeIdleWatchdog {
    fun enabled(environment: Map<String, String> = System.getenv()): Boolean =
        environment["GITHUB_ACTIONS"] == "true" && environment["GITHUB_REF_NAME"]?.startsWith("ai-") == true

    fun <T> observe(label: String, delaysMillis: List<Long> = listOf(20_000L, 45_000L),
        emit: (String) -> Unit = { System.err.println(it) }, action: () -> T): T {
        val active = AtomicBoolean(true)
        val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "compose-idle-watchdog").apply { isDaemon = true }
        }
        try {
            delaysMillis.take(2).forEach { delay ->
                scheduler.schedule({
                    if (active.get()) runCatching {
                        val report = snapshot(label, delay)
                        if (active.get()) emit(report)
                    }
                }, delay.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            }
            return action()
        } finally {
            active.set(false)
            scheduler.shutdownNow()
        }
    }

    private fun snapshot(label: String, delay: Long): String = buildString {
        appendLine("[compose-idle] " + label.take(200) + " after " + delay + " ms")
        val threads = Thread.getAllStackTraces().entries
            .filter { it.key.name != "compose-idle-watchdog" }
            .sortedWith(compareBy({ entry ->
                !entry.value.any { it.className.startsWith("androidx.compose") ||
                    it.className.startsWith("org.robolectric") }
            }, { it.key.name }))
        threads.take(40).forEach { (thread, stack) ->
            appendLine(thread.name.take(150) + " [" + thread.state + "]")
            stack.take(40).forEach { appendLine("  at " + it) }
        }
        if (threads.size > 40) appendLine("Additional threads omitted: " + (threads.size - 40))
        appendLine("[compose-idle] end")
    }
}
