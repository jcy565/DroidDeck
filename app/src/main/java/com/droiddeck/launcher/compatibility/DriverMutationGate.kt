package com.droiddeck.launcher.compatibility

import java.util.concurrent.locks.ReentrantLock

/** Process-local coordination for local driver file mutations.
 * Never hold this lock during a network download or wait for it on the UI thread. */
internal class DriverMutationGate(private val writesAllowed: () -> Boolean) {
    private val lock = ReentrantLock()

    fun <T> mutate(blockedMessage: String, action: () -> T): T {
        check(lock.tryLock()) { blockedMessage }
        try {
            check(writesAllowed()) { blockedMessage }
            return action()
        } finally {
            lock.unlock()
        }
    }

    /** Called on the session worker after running has been published.
     * Any previously admitted mutation must finish before guest startup. */
    fun awaitCurrentMutation() {
        lock.lock()
        try {
            // Acquire/release is the barrier.
        } finally {
            lock.unlock()
        }
    }

    companion object {
        val shared = DriverMutationGate {
            GameplayBudget.allows(GameplayBudget.Work.SETTINGS_WRITE)
        }
    }
}
