package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest

/** Caller must supply a trusted installed game root, not a generic browser start directory.
 * This is an approval snapshot, not a lock against concurrent external writers.
 */
internal object GameSettingsApproval {
    data class Plan internal constructor(
        val root: String, val relative: String, val mode: GameFileAnalysis.Mode,
        val originalHash: String, val proposedHash: String,
        val changes: List<GameConfigPreview.Change>
    )

    private fun digest(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun scoped(root: File, file: File): Pair<File, String> {
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
        val base = root.canonicalFile
        val path = file.absoluteFile.toPath()
        val origin = root.absoluteFile.toPath()
        require(path.startsWith(origin) && path != origin)
        val relative = origin.relativize(path)
        require(relative.none { it.toString() in listOf(".", "..") || '\\' in it.toString() })
        var current = base
        for (part in relative) {
            current = File(current, part.toString())
            require(!Files.isSymbolicLink(current.toPath()))
        }
        require(current.canonicalFile == current && Files.isRegularFile(current.toPath(), NOFOLLOW_LINKS))
        require(current.extension.lowercase(java.util.Locale.ROOT) in setOf("ini", "cfg"))
        return current to relative.joinToString("/")
    }

    private data class Snapshot(val plan: Plan, val original: ByteArray, val proposed: ByteArray)

    private fun snapshot(root: File, file: File, mode: GameFileAnalysis.Mode, checkpoint: () -> Unit): Snapshot {
        checkpoint()
        require(mode != GameFileAnalysis.Mode.LOG)
        val (target, relative) = scoped(root, file)
        val original = Files.newInputStream(target.toPath(), NOFOLLOW_LINKS).use {
            GamePatchStore.readLimited(it, checkpoint)
        }
        val preview = LocalGameAdvisor.optimize(LocalGameAdvisor.decode(original), mode.command)
        require(preview.changes.isNotEmpty()) { "No changes to approve" }
        val proposed = preview.proposed.toByteArray(Charsets.UTF_8)
        checkpoint()
        return Snapshot(Plan(root.canonicalPath, relative, mode, digest(original), digest(proposed),
            preview.changes.toList()), original, proposed)
    }

    fun prepare(root: File, file: File, mode: GameFileAnalysis.Mode, checkpoint: () -> Unit): Plan =
        snapshot(root, file, mode, checkpoint).plan

    /** Call only after the user explicitly confirms the exact preview. */
    fun apply(root: File, file: File, approved: Plan, backupDir: File, checkWrite: () -> Unit): File {
        val current = snapshot(root, file, approved.mode, checkWrite)
        require(current.plan == approved) { "File or preview changed; review again" }
        val (target, relative) = scoped(root, file)
        require(relative == approved.relative && root.canonicalPath == approved.root)
        return GamePatchStore.apply(root.canonicalFile, relative, current.original, current.proposed, backupDir) {
            checkWrite()
            require(scoped(root, file).first == target && root.canonicalPath == approved.root)
        }
    }
}
