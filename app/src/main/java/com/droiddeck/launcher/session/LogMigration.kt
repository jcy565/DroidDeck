package com.droiddeck.launcher.session

import android.content.Context

object LogMigration {
    /**
     * The independent AI app must never claim or move the official app's public
     * Downloads/DroidDeck logs. Existing logs remain where their owner left them.
     * New sessions already use LinuxRuntime.logDir(context), an app-private path.
     */
    @Synchronized
    fun run(@Suppress("UNUSED_PARAMETER") context: Context) = Unit
}
