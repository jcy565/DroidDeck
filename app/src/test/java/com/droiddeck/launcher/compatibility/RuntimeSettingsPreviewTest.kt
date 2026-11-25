package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class RuntimeSettingsPreviewTest {
    private val snapshot = RuntimeSettingsPreview.Snapshot("linux-old", "android-old", "fex-old")

    @Test fun previewsEachSetting() {
        val expected = listOf("linux-old", "android-old", "fex-old")
        RuntimeSettingsPreview.Setting.values().forEachIndexed { index, setting ->
            val change = RuntimeSettingsPreview.preview(snapshot, setting, "candidate", setOf("candidate"))
            assertEquals(setting, change.setting)
            assertEquals(expected[index], change.before)
            assertEquals("candidate", change.proposed)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownCandidate() {
        RuntimeSettingsPreview.preview(snapshot, RuntimeSettingsPreview.Setting.LINUX_DRIVER, "missing", emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankCandidate() {
        RuntimeSettingsPreview.preview(snapshot, RuntimeSettingsPreview.Setting.FEX_PRESET, " ", setOf(" "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsControlCharacters() {
        val id = "bad\nvalue"
        RuntimeSettingsPreview.preview(snapshot, RuntimeSettingsPreview.Setting.ANDROID_DRIVER, id, setOf(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedCandidate() {
        val id = "x".repeat(257)
        RuntimeSettingsPreview.preview(snapshot, RuntimeSettingsPreview.Setting.LINUX_DRIVER, id, setOf(id))
    }
}
