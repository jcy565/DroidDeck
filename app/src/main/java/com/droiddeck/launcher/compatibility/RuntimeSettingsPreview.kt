package com.droiddeck.launcher.compatibility

import android.content.Context
import com.droiddeck.launcher.session.SessionPrefs

/** Read-only: candidate IDs must come from the existing component selector,
 * not from guessed driver names. No installation or environment writes. */
internal object RuntimeSettingsPreview {
    enum class Setting { LINUX_DRIVER, ANDROID_DRIVER, FEX_PRESET }
    data class Snapshot(val linuxDriver: String, val androidDriver: String, val fexPreset: String)
    data class Change(val setting: Setting, val before: String, val proposed: String)

    fun inspect(context: Context): Snapshot = Snapshot(
        SessionPrefs.linuxDriver(context),
        SessionPrefs.androidDriver(context),
        SessionPrefs.fexPreset(context)
    )

    /** Uses the existing selector's local rows, never its download catalog.
     * Default/automatic IDs are excluded from explicit driver experiments.
     */
    fun driverCandidates(state: com.droiddeck.launcher.ui.GpuDriversState,
        setting: Setting): Set<String> = when (setting) {
        Setting.LINUX_DRIVER -> state.linuxRows.map { it.id }.filter { it.isNotBlank() }.toSet()
        Setting.ANDROID_DRIVER -> state.androidRows.map { it.id }
            .filter { it.isNotBlank() && it != com.droiddeck.launcher.gpu.TurnipDriver.AUTO }.toSet()
        Setting.FEX_PRESET -> emptySet()
    }

    data class DriverOption(val change: Change, val name: String, val detail: String)
    data class DriverComparison(val linuxSelected: String, val androidSelected: String,
        val linux: List<DriverOption>, val android: List<DriverOption>)

    /** Snapshot the selector's existing local inventory only; no refresh, install or writes. */
    fun compareLocalDrivers(state: com.droiddeck.launcher.ui.GpuDriversState): DriverComparison {
        check(state.busy == null && !state.checking)
        val snapshot = Snapshot(state.linuxSelected, state.androidSelected, "")
        fun options(setting: Setting, rows: List<com.droiddeck.launcher.ui.DriverRow>): List<DriverOption> {
            val supported = driverCandidates(state, setting)
            return rows.asSequence().filter { it.id in supported && it.id.length <= 256 &&
                it.id.none { char -> char.isISOControl() } }.distinctBy { it.id }
                .map { DriverOption(preview(snapshot, setting, it.id, supported), it.name, it.detail) }.toList()
        }
        return DriverComparison(state.linuxSelected, state.androidSelected,
            options(Setting.LINUX_DRIVER, state.linuxRows), options(Setting.ANDROID_DRIVER, state.androidRows))
    }

    /** Only ordinary compatibility/performance presets exposed by the existing
     * selector. Default/experimental/special-purpose presets are not suggested. */
    fun fexCandidates(): Set<String> {
        val supported = setOf("STABILITY", "COMPATIBILITY", "INTERMEDIATE", "PERFORMANCE_TSO", "PERFORMANCE")
        return com.droiddeck.launcher.core.FexPreset.all.map { it.id }
            .filter { it in supported }.toSet()
    }

    fun fexAlternatives(snapshot: Snapshot): List<Change> {
        val candidates = fexCandidates()
        return candidates.map { preview(snapshot, Setting.FEX_PRESET, it, candidates) }
    }

    fun preview(snapshot: Snapshot, setting: Setting, proposed: String,
        supportedIds: Set<String>): Change {
        require(proposed.isNotBlank() && proposed.length <= 256)
        require(proposed.none { it.isISOControl() })
        require(proposed in supportedIds) { "지원 목록에 없는 설정입니다." }
        val before = when (setting) {
            Setting.LINUX_DRIVER -> snapshot.linuxDriver
            Setting.ANDROID_DRIVER -> snapshot.androidDriver
            Setting.FEX_PRESET -> snapshot.fexPreset
        }
        return Change(setting, before, proposed)
    }
}
