package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SessionDriverGateTest {
    private val gate = SessionDriverGate()
    private val session = SessionDriverGate.Kind.SESSION
    private val driver = SessionDriverGate.Kind.DRIVER_WRITE

    @Test fun sessionExcludesDriverAndOtherSession() {
        gate.tryAcquire(session)!!.use {
            assertNull(gate.tryAcquire(driver))
            assertNull(gate.tryAcquire(session))
        }
        gate.tryAcquire(driver)!!.close()
    }

    @Test fun driverExcludesSessionAndOtherDriver() {
        gate.tryAcquire(driver)!!.use {
            assertNull(gate.tryAcquire(session))
            assertNull(gate.tryAcquire(driver))
        }
        gate.tryAcquire(session)!!.close()
    }

    @Test fun failedAdmissionDoesNotReserveGate() {
        assertNull(gate.tryAcquire(driver) { false })
        gate.tryAcquire(session)!!.close()
    }

    @Test fun staleLeaseCannotReleaseNewOwner() {
        val old = gate.tryAcquire(driver)!!
        old.close()
        gate.tryAcquire(session)!!.use {
            old.close()
            assertNull(gate.tryAcquire(driver))
        }
        gate.tryAcquire(driver)!!.close()
    }

    @Test fun exceptionReleasesLease() {
        try {
            gate.tryAcquire(driver)!!.use { throw IllegalStateException("test") }
        } catch (_: IllegalStateException) {
        }
        gate.tryAcquire(session)!!.close()
    }

    @Test fun leaseCanBeReleasedFromAnotherThread() {
        val executor = Executors.newSingleThreadExecutor()
        val lease = gate.tryAcquire(driver)!!
        try {
            assertNull(executor.submit<SessionDriverGate.Lease?> {
                gate.tryAcquire(session)
            }.get(5, TimeUnit.SECONDS))
            executor.submit { lease.close() }.get(5, TimeUnit.SECONDS)
            gate.tryAcquire(session)!!.close()
        } finally {
            lease.close()
            executor.shutdownNow()
        }
    }
}
