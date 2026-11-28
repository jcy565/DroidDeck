package com.droiddeck.launcher.ui

/** A layout callback is not necessarily a movement. Keep only the active source's last report. */
internal class FocusMovementGate<S : Any, B : Any> {
    private var source: S? = null
    private var bounds: B? = null

    fun changed(active: S, currentBounds: B?): Boolean {
        if (source === active && bounds == currentBounds) return false
        source = active
        bounds = currentBounds
        return true
    }

    fun reset() {
        source = null
        bounds = null
    }
}
