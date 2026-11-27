package com.droiddeck.launcher.update

/** Independent AI builds may read upstream news but must not use the official APK installer. */
internal object OfficialUpdatePolicy {
    private const val AI_ID = "com.droiddeck.launcher.ai"

    fun isIndependentAi(packageName: String): Boolean =
        packageName == AI_ID || packageName.startsWith("$AI_ID.")

    fun allowsOfficialInstall(packageName: String): Boolean = !isIndependentAi(packageName)
}
