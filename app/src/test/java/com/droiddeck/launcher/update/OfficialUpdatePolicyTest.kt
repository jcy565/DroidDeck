package com.droiddeck.launcher.update

import org.junit.Assert.*
import org.junit.Test

class OfficialUpdatePolicyTest {
    @Test fun aiPackageAndItsVariantsAreIsolated() {
        for (id in listOf("com.droiddeck.launcher.ai", "com.droiddeck.launcher.ai.debug",
            "com.droiddeck.launcher.ai.dev.local")) {
            assertTrue(OfficialUpdatePolicy.isIndependentAi(id))
            assertFalse(OfficialUpdatePolicy.allowsOfficialInstall(id))
        }
    }

    @Test fun unrelatedPackageNamesAreNotMistakenForAiVariants() {
        for (id in listOf("com.droiddeck.launcher", "com.droiddeck.launcher.debug",
            "com.droiddeck.launcher.aitest")) {
            assertFalse(OfficialUpdatePolicy.isIndependentAi(id))
            assertTrue(OfficialUpdatePolicy.allowsOfficialInstall(id))
        }
    }
}
