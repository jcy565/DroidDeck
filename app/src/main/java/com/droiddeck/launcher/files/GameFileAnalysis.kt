package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** Explicit local, read-only analysis. No file changes, network or raw log export. */
internal object GameFileAnalysis {
    enum class Mode(val command: String) {
        LOG(""), RESOLUTION_720("720"), RESOLUTION_900("900"), FRAME_LIMIT_30("30")
    }
    data class Result(val diagnosis: String?, val changes: List<GameConfigPreview.Change>)

    fun supportsName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) in setOf("log", "ini", "cfg")

    fun analyze(file: File, mode: Mode, checkpoint: () -> Unit = {}): Result {
        checkpoint()
        require(supportsName(file.name))
        require((mode == Mode.LOG) == file.extension.equals("log", true))
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use {
            GamePatchStore.readLimited(it, checkpoint)
        }
        checkpoint()
        val text = LocalGameAdvisor.decode(bytes)
        checkpoint()
        val result = if (mode == Mode.LOG) Result(LocalGameAdvisor.diagnose(text), emptyList())
            else Result(null, LocalGameAdvisor.optimize(text, mode.command).changes)
        checkpoint()
        return result
    }
}
