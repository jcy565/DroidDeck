package com.droiddeck.launcher.ui

import org.junit.Assert.*
import org.junit.Test

class FocusMovementGateTest {
    private data class Bounds(val x: Int, val width: Int)
    private data class Source(val key: String)

    @Test fun identicalPlacementDoesNotRestartTracking() {
        val gate = FocusMovementGate<Source, Bounds>()
        val active = Source("row")
        assertTrue(gate.changed(active, null))
        repeat(1000) { assertFalse(gate.changed(active, null)) }
        assertTrue(gate.changed(active, Bounds(10, 20)))
        repeat(1000) { assertFalse(gate.changed(active, Bounds(10, 20))) }
        assertTrue(gate.changed(active, Bounds(11, 20)))
        assertTrue(gate.changed(active, Bounds(11, 21)))
        assertTrue(gate.changed(active, null))
        assertFalse(gate.changed(active, null))
    }

    @Test fun equalButDistinctSourceAndResetStillNotify() {
        val gate = FocusMovementGate<Source, Bounds>()
        val first = Source("same")
        val second = Source("same")
        val bounds = Bounds(0, 50)
        assertTrue(gate.changed(first, bounds))
        assertTrue(gate.changed(second, bounds))
        assertFalse(gate.changed(second, bounds))
        gate.reset()
        assertTrue(gate.changed(second, bounds))
    }
}
