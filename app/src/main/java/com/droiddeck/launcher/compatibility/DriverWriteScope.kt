package com.droiddeck.launcher.compatibility

/** Synchronous, same-thread nesting for driver menu mutations.
 * A nested call may reuse its own scope, never a different thread's lease.
 * Do not run network downloads or await workers inside the scope. */
internal class DriverWriteScope(
    private val gate: SessionDriverGate,
    private val allowed: () -> Boolean,
) {
    private val entered = ThreadLocal<Boolean>()

    fun <T> run(onBlocked: () -> T, action: () -> T): T {
        if (entered.get() == true) return action()
        val lease = gate.tryAcquire(SessionDriverGate.Kind.DRIVER_WRITE, allowed)
            ?: return onBlocked()
        return lease.use {
            entered.set(true)
            try {
                action()
            } finally {
                entered.remove()
            }
        }
    }
}
