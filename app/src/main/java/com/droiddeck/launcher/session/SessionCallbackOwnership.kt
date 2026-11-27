package com.droiddeck.launcher.session

/** A callback belongs to the session that registered it, not the latest global session. */
internal object SessionCallbackOwnership {
    fun owns(owner: String?, current: String?): Boolean = owner != null && owner == current

    fun acceptsActive(owner: String?, current: String?, phase: SessionPhase): Boolean =
        owns(owner, current) && phase !in setOf(SessionPhase.IDLE, SessionPhase.FAILED, SessionPhase.STOPPING)
}
