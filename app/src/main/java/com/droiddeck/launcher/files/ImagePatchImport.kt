package com.droiddeck.launcher.files

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Read-only inspection against a selected game root. No archive extraction. */
internal object ImagePatchImport {
    enum class State { READY, ALREADY_APPLIED, UNCHANGED, MISSING, UNSAFE, MISMATCH, INVALID_OUTPUT, READ_FAILED }
    data class Entry(val row: ImagePatchArchive.Row, val state: State)
    data class Report(val root: String, val file: File, val digest: String, val entries: List<Entry>)

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun inspectTarget(root: File, row: ImagePatchArchive.Row, output: ByteArray,
        verify: (ByteArray, ImagePatchArchive.Source) -> Unit, checkpoint: () -> Unit): State {
        try { verify(output, row.source) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { return State.INVALID_OUTPUT }
        return try {
            var path = root.canonicalFile.toPath()
            val parts = row.source.relative.split('/')
            for ((index, part) in parts.withIndex()) {
                checkpoint()
                path = path.resolve(part)
                if (Files.isSymbolicLink(path)) return State.UNSAFE
                if (Files.notExists(path, NOFOLLOW_LINKS)) return State.MISSING
                if (index < parts.lastIndex && !Files.isDirectory(path, NOFOLLOW_LINKS)) return State.UNSAFE
            }
            if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) return State.UNSAFE
            val length = Files.size(path)
            if (length != row.source.originalBytes && length != row.outputBytes.toLong()) return State.MISMATCH
            val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
                val data = ByteArray(length.toInt())
                var offset = 0
                while (offset < data.size) {
                    checkpoint()
                    val n = input.read(data, offset, minOf(65536, data.size - offset))
                    if (n < 0) return State.MISMATCH
                    offset += n
                }
                if (input.read() != -1) return State.MISMATCH
                data
            }
            val digest = hash(bytes)
            when {
                digest == row.outputHash && length == row.outputBytes.toLong() &&
                    digest == row.source.originalHash && length == row.source.originalBytes -> State.UNCHANGED
                digest == row.outputHash && length == row.outputBytes.toLong() -> State.ALREADY_APPLIED
                digest == row.source.originalHash && length == row.source.originalBytes -> {
                    verify(bytes, row.source)
                    State.READY
                }
                else -> State.MISMATCH
            }
        } catch (e: CancellationException) { throw e }
          catch (_: Exception) { State.READ_FAILED }
    }

    fun inspect(root: File, file: File, verify: (ByteArray, ImagePatchArchive.Source) -> Unit,
        checkpoint: () -> Unit = {}): Report {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val canonical = root.canonicalFile
        val before = ImagePatchArchive.digest(file, checkpoint)
        val entries = mutableListOf<Entry>()
        ImagePatchArchive.inspectPayloads(file, checkpoint) { row, bytes ->
            entries.add(Entry(row, inspectTarget(canonical, row, bytes, verify, checkpoint)))
        }
        require(ImagePatchArchive.digest(file, checkpoint) == before) { "패치 파일이 변경됐습니다." }
        require(root.canonicalFile == canonical && Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        checkpoint()
        return Report(canonical.path, file, before, entries.toList())
    }

    fun copyAndInspect(root: File, directory: File, input: InputStream,
        verify: (ByteArray, ImagePatchArchive.Source) -> Unit, checkpoint: () -> Unit = {}): Report {
        checkpoint()
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val game = root.canonicalFile.toPath()
        val dest = directory.canonicalFile.toPath()
        require(!game.startsWith(dest) && !dest.startsWith(game))
        require(!Files.isSymbolicLink(directory.toPath()))
        require(directory.isDirectory || directory.mkdirs())
        val file = Files.createTempFile(directory.toPath(), "image-import-", ".zip").toFile()
        try {
            FileOutputStream(file).use { out ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                    checkpoint()
                    val n = input.read(buffer)
                    checkpoint()
                    if (n < 0) break
                    require(n > 0)
                    total += n; require(total <= ImagePatchArchive.MAX_ARCHIVE)
                    out.write(buffer, 0, n)
                }
                out.fd.sync()
            }
            return inspect(root, file, verify, checkpoint)
        } catch (failure: Throwable) {
            Files.deleteIfExists(file.toPath())
            throw failure
        }
    }
}
