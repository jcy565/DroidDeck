package com.droiddeck.launcher.compatibility

import java.io.Closeable

/**
 * Process-local, nonblocking admission gate for session startup and driver mutations.
 * A lease is transferable between threads and must be closed on every exit path.
 * The monitor protects admission only, never a download, install or session lifetime.
 */
internal class SessionDriverGate {
    enum class Kind { SESSION, DRIVER_WRITE }

    private var active: Lease? = null

    class Lease internal constructor(
        private val gate: SessionDriverGate,
        val kind: Kind,
    ) : Closeable {
        override fun close() = gate.release(this)
    }

    @Synchronized
    fun tryAcquire(kind: Kind, allowed: () -> Boolean = { true }): Lease? {
        if (active != null || !allowed()) return null
        return Lease(this, kind).also { active = it }
    }

    @Synchronized
    private fun release(lease: Lease) {
        if (active === lease) active = null
    }

    companion object {
        val shared = SessionDriverGate()
    }
}
