package com.droiddeck.launcher.compatibility

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class SessionDriverGateConcurrencyTest {
    private val session = SessionDriverGate.Kind.SESSION
    private val driver = SessionDriverGate.Kind.DRIVER_WRITE

    @Test fun sessionExcludesDriverAndDuplicateSession() {
        val gate = SessionDriverGate()
        requireNotNull(gate.tryAcquire(session)).use {
            assertNull(gate.tryAcquire(driver))
            assertNull(gate.tryAcquire(session))
        }
        requireNotNull(gate.tryAcquire(driver)).close()
    }

    @Test fun driverExcludesSessionAndOtherDriver() {
        val gate = SessionDriverGate()
        requireNotNull(gate.tryAcquire(driver)).use {
            assertNull(gate.tryAcquire(session))
            assertNull(gate.tryAcquire(driver))
        }
        requireNotNull(gate.tryAcquire(session)).close()
    }

    @Test fun rejectedPredicateDoesNotReserveGate() {
        val gate = SessionDriverGate()
        assertNull(gate.tryAcquire(driver) { false })
        requireNotNull(gate.tryAcquire(session)).close()
    }

    @Test fun staleCloseDoesNotReleaseCurrentLease() {
        val gate = SessionDriverGate()
        val old = requireNotNull(gate.tryAcquire(driver))
        old.close()
        requireNotNull(gate.tryAcquire(session)).use {
            old.close()
            assertNull(gate.tryAcquire(driver))
        }
    }

    @Test fun leaseCanBeReleasedByAnotherThread() {
        val gate = SessionDriverGate()
        val lease = requireNotNull(gate.tryAcquire(driver))
        val worker = Executors.newSingleThreadExecutor()
        try {
            assertTrue(worker.submit<Boolean> { gate.tryAcquire(session) == null }.get(5, TimeUnit.SECONDS))
            worker.submit { lease.close() }.get(5, TimeUnit.SECONDS)
            requireNotNull(gate.tryAcquire(session)).close()
        } finally {
            lease.close()
            worker.shutdownNow()
        }
    }

    @Test fun simultaneousAdmissionHasOnlyOneWinner() {
        val gate = SessionDriverGate()
        val worker = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        try {
            val attempts = listOf(session, driver).map { kind ->
                worker.submit<SessionDriverGate.Lease?> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    gate.tryAcquire(kind)
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            val leases = attempts.mapNotNull { it.get(5, TimeUnit.SECONDS) }
            try {
                assertEquals(1, leases.size)
            } finally {
                leases.forEach { it.close() }
            }
            requireNotNull(gate.tryAcquire(session)).close()
        } finally {
            start.countDown()
            worker.shutdownNow()
        }
    }
}
