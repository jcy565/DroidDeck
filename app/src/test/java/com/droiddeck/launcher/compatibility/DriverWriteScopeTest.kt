package com.droiddeck.launcher.compatibility

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DriverWriteScopeTest {
    private val gate = SessionDriverGate()
    private val driver = SessionDriverGate.Kind.DRIVER_WRITE
    private val session = SessionDriverGate.Kind.SESSION

    @Test fun nestedCallsReuseSameThreadAdmission() {
        val scope = DriverWriteScope(gate) { true }
        val value = scope.run(onBlocked = { -1 }) {
            assertNull(gate.tryAcquire(session))
            scope.run(onBlocked = { -2 }) { 42 }
        }
        assertEquals(42, value)
        requireNotNull(gate.tryAcquire(session)).close()
    }

    @Test fun sessionBlocksScopeWithoutRunningAction() {
        val scope = DriverWriteScope(gate) { true }
        requireNotNull(gate.tryAcquire(session)).use {
            assertEquals("blocked", scope.run(onBlocked = { "blocked" }) { error("must not run") })
        }
        assertEquals("ok", scope.run(onBlocked = { "blocked" }) { "ok" })
    }

    @Test fun predicateRefusalDoesNotLeakLease() {
        val scope = DriverWriteScope(gate) { false }
        assertFalse(scope.run(onBlocked = { false }) { true })
        requireNotNull(gate.tryAcquire(driver)).close()
    }

    @Test fun exceptionsReleaseBothLeaseAndThreadLocal() {
        val scope = DriverWriteScope(gate) { true }
        assertThrows(IllegalStateException::class.java) {
            scope.run(onBlocked = {}) {
                scope.run(onBlocked = {}) { error("failure") }
            }
        }
        requireNotNull(gate.tryAcquire(session)).use {
            assertFalse(scope.run(onBlocked = { false }) { true })
        }
        assertTrue(scope.run(onBlocked = { false }) { true })
    }

    @Test fun anotherThreadCannotReuseActiveScope() {
        val scope = DriverWriteScope(gate) { true }
        val worker = Executors.newSingleThreadExecutor()
        try {
            scope.run(onBlocked = { error("unexpected rejection") }) {
                assertFalse(worker.submit<Boolean> {
                    scope.run(onBlocked = { false }) { true }
                }.get(5, TimeUnit.SECONDS))
                assertNull(gate.tryAcquire(driver))
            }
            assertTrue(worker.submit<Boolean> {
                scope.run(onBlocked = { false }) { true }
            }.get(5, TimeUnit.SECONDS))
        } finally {
            worker.shutdownNow()
        }
    }

    @Test fun anotherScopeCannotReuseActiveScope() {
        val first = DriverWriteScope(gate) { true }
        val second = DriverWriteScope(gate) { true }
        first.run(onBlocked = { error("unexpected rejection") }) {
            assertFalse(second.run(onBlocked = { false }) { true })
        }
        assertTrue(second.run(onBlocked = { false }) { true })
    }
}
