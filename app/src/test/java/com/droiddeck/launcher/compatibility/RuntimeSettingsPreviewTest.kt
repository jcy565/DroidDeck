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

    @Test fun localDriverComparisonExcludesAutoDownloadsAndInvalidIds() {
        fun row(id: String) = com.droiddeck.launcher.ui.DriverRow(id, id, "", true)
        val auto = com.droiddeck.launcher.gpu.TurnipDriver.AUTO
        val state = com.droiddeck.launcher.ui.GpuDriversState(
            linuxRows = listOf(row(""), row("linux-local"), row("linux-local"), row("bad\nvalue"), row("x".repeat(257))),
            androidRows = listOf(row(auto), row("android-local")),
            linuxSelected = "linux-current", androidSelected = auto,
            linuxDownloads = listOf(com.droiddeck.launcher.ui.DownloadRow("remote", "Remote", "")),
        )
        val result = RuntimeSettingsPreview.compareLocalDrivers(state)
        assertEquals("linux-current", result.linuxSelected)
        assertEquals(auto, result.androidSelected)
        assertEquals(listOf("linux-local"), result.linux.map { it.change.proposed })
        assertEquals(listOf("android-local"), result.android.map { it.change.proposed })
        assertEquals("linux-current", result.linux.single().change.before)
        assertEquals(auto, result.android.single().change.before)
    }

    @Test fun localDriverComparisonRefusesBusySelector() {
        assertThrows(IllegalStateException::class.java) {
            RuntimeSettingsPreview.compareLocalDrivers(com.droiddeck.launcher.ui.GpuDriversState(busy = "pair"))
        }
        assertThrows(IllegalStateException::class.java) {
            RuntimeSettingsPreview.compareLocalDrivers(com.droiddeck.launcher.ui.GpuDriversState(checking = true))
        }
    }

    @Test fun fexAlternativesUseOrdinaryExistingPresetsOnly() {
        val choices = RuntimeSettingsPreview.fexAlternatives(snapshot)
        val ids = choices.map { it.proposed }.toSet()
        assertEquals(setOf("STABILITY", "COMPATIBILITY", "INTERMEDIATE", "PERFORMANCE_TSO", "PERFORMANCE"), ids)
        assertTrue(ids.all { id -> com.droiddeck.launcher.core.FexPreset.all.any { it.id == id } })
        assertTrue(choices.all { it.setting == RuntimeSettingsPreview.Setting.FEX_PRESET && it.before == snapshot.fexPreset })
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
