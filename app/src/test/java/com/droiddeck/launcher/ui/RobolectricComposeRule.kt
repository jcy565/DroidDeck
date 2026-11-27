package com.droiddeck.launcher.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.snapshots.Snapshot
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

fun createRobolectricComposeRule(): ComposeContentTestRule {
    val delegate = createComposeRule()
    RingVeil.reset()
    val context = RuntimeEnvironment.getApplication()
    shadowOf(context.packageManager).addActivityIfNotPresent(
        ComponentName(context, ComponentActivity::class.java),
    )
    Snapshot.sendApplyNotifications()
    if (!ComposeIdleWatchdog.enabled()) return delegate
    return object : ComposeContentTestRule by delegate {
        override fun apply(base: Statement, description: Description): Statement {
            val statement = delegate.apply(base, description)
            return object : Statement() {
                override fun evaluate() {
                    ComposeIdleWatchdog.observe(description.displayName) { statement.evaluate() }
                }
            }
        }
    }
}
