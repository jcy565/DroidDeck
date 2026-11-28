package com.droiddeck.launcher.files

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.PosixFileAttributeView
import java.security.MessageDigest
import java.util.UUID

/** Experimental transaction core. Requires a stopped game and a secure POSIX directory provider. */
internal object ImagePatchTransaction {
    enum class Phase { PREPARED, APPLYING, APPLIED, RESTORED }
    data class Journal(val phase: Phase, val attempted: Int)
    data class Recovery(val restored: Int, val unchanged: Int, val conflicts: List<String>)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun journalFile(b: ImagePatchBackup.Backup) = File(b.directory, "transaction.ddit")
    private fun valid(j: Journal, b: ImagePatchBackup.Backup) {
        require(j.attempted in 0..b.rows.size)
        require(when (j.phase) {
            Phase.PREPARED, Phase.RESTORED -> j.attempted == 0
            Phase.APPLYING -> j.attempted > 0
            Phase.APPLIED -> j.attempted == b.rows.size
        })
    }
    private fun readJournal(b: ImagePatchBackup.Backup, checkpoint: () -> Unit): Journal {
        checkpoint()
        val file = journalFile(b)
        // An inaccessible journal is not equivalent to a missing journal. Fail closed.
        if (Files.notExists(file.toPath(), NOFOLLOW_LINKS)) return Journal(Phase.PREPARED, 0)
        require(Files.exists(file.toPath(), NOFOLLOW_LINKS)) { "작업 기록의 존재 여부를 확인할 수 없습니다." }
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() in 1L..512L)
        val text = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val bytes = ByteArray(513)
            var n = 0
            while (n < bytes.size) {
                checkpoint()
                val count = input.read(bytes, n, bytes.size - n)
                checkpoint()
                if (count < 0) break
                require(count > 0)
                n += count
            }
            require(n <= 512)
            String(bytes, 0, n, Charsets.UTF_8)
        }
        val parts = text.split('\n')
        require(parts.size == 5 && parts[0] == "DDIMAGETXN1" && parts[1] == b.patchDigest)
        val body = parts.take(4).joinToString("\n")
        require(hash(body.toByteArray(Charsets.UTF_8)) == parts[4])
        checkpoint()
        return Journal(Phase.valueOf(parts[2]), parts[3].toInt()).also { valid(it, b) }
    }
    private fun writeJournal(b: ImagePatchBackup.Backup, j: Journal) {
        valid(j, b)
        val file = journalFile(b).toPath()
        require(Files.notExists(file, NOFOLLOW_LINKS) || Files.isRegularFile(file, NOFOLLOW_LINKS))
        val body = listOf("DDIMAGETXN1", b.patchDigest, j.phase.name, j.attempted.toString()).joinToString("\n")
        val temp = Files.createTempFile(b.directory.toPath(), ".transaction-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { out ->
                out.write((body + "\n" + hash(body.toByteArray(Charsets.UTF_8))).toByteArray(Charsets.UTF_8)); out.fd.sync()
            }
            Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    /** Walk using open directory handles; refuse providers without SecureDirectoryStream. */
    private fun <T> parent(root: File, relative: String, action: (SecureDirectoryStream<Path>, Path) -> T): T {
        val base = root.toPath().toAbsolutePath().normalize()
        require(base == root.canonicalFile.toPath() && Files.isDirectory(base, NOFOLLOW_LINKS))
        val parts = relative.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." && '\\' !in it })
        val directories = base.map { it.toString() } + parts.dropLast(1)
        fun descend(handle: SecureDirectoryStream<Path>, index: Int): T {
            if (index == directories.size) return action(handle, base.fileSystem.getPath(parts.last()))
            return handle.newDirectoryStream(base.fileSystem.getPath(directories[index]), NOFOLLOW_LINKS).use {
                descend(it, index + 1)
            }
        }
        return Files.newDirectoryStream(requireNotNull(base.root)).use { stream ->
            require(stream is SecureDirectoryStream<Path>) { "안전한 디렉터리 교체를 지원하지 않는 저장소입니다." }
            descend(stream, 0)
        }
    }
    private fun attrs(dir: SecureDirectoryStream<Path>, name: Path) =
        requireNotNull(dir.getFileAttributeView(name, PosixFileAttributeView::class.java, NOFOLLOW_LINKS)).readAttributes()

    private fun current(dir: SecureDirectoryStream<Path>, name: Path, checkpoint: () -> Unit): String {
        val before = attrs(dir, name)
        require(before.isRegularFile && before.fileKey() != null && before.size() in 1L..ImagePatchArchive.MAX_FILE.toLong())
        val digest = MessageDigest.getInstance("SHA-256")
        dir.newByteChannel(name, setOf(READ, NOFOLLOW_LINKS)).use { channel ->
            val buffer = ByteBuffer.allocate(65536)
            var total = 0L
            while (true) {
                checkpoint(); buffer.clear()
                val n = channel.read(buffer)
                checkpoint()
                if (n < 0) break
                total += n; require(total <= ImagePatchArchive.MAX_FILE)
                digest.update(buffer.array(), 0, n)
            }
            require(total == before.size())
        }
        val after = attrs(dir, name)
        require(before.fileKey() == after.fileKey() && before.size() == after.size() && before.lastModifiedTime() == after.lastModifiedTime())
        checkpoint()
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun replace(root: File, row: ImagePatchArchive.Row, expected: String, desired: String,
        bytes: ByteArray, checkpoint: () -> Unit) = parent(root, row.source.relative) { dir, name ->
        require(hash(bytes) == desired && current(dir, name, checkpoint) == expected)
        val before = attrs(dir, name)
        val temp = name.fileSystem.getPath(".dd-image-${UUID.randomUUID()}.tmp")
        var created = false
        try {
            dir.newByteChannel(temp, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS)).use { channel ->
                created = true
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) { checkpoint(); channel.write(buffer) }
                require(channel is FileChannel)
                channel.force(true)
            }
            val view = requireNotNull(dir.getFileAttributeView(temp, PosixFileAttributeView::class.java, NOFOLLOW_LINKS))
            view.setPermissions(before.permissions())
            val staged = attrs(dir, temp)
            require(staged.owner() == before.owner() && staged.group() == before.group() && staged.permissions() == before.permissions())
            require(current(dir, temp, checkpoint) == desired && current(dir, name, checkpoint) == expected)
            require(attrs(dir, name).fileKey() == before.fileKey())
            checkpoint()
            dir.move(temp, dir, name)
            created = false
            require(current(dir, name, checkpoint) == desired)
        } finally { if (created) dir.deleteFile(temp) }
    }

    enum class AssetState { ORIGINAL, TRANSLATED, CONFLICT, UNAVAILABLE }
    data class AssetCheck(val relative: String, val state: AssetState)
    data class Diagnostic(val journal: Journal, val assets: List<AssetCheck>) {
        val assessment: ImagePatchReview.Assessment get() = ImagePatchReview.assess(journal, assets)
    }

    /** Read-only snapshot of verified backup, journal and actual game contents. */
    @Synchronized
    fun diagnose(storage: File, root: File, checkpoint: () -> Unit = {}): Diagnostic {
        val backup = requireNotNull(ImagePatchBackup.load(storage, root, checkpoint))
        val journal = readJournal(backup, checkpoint)
        val assets = backup.rows.map { row ->
            checkpoint()
            val state = try {
                val actual = parent(root, row.source.relative) { dir, name -> current(dir, name, checkpoint) }
                when (actual) {
                    row.source.originalHash -> AssetState.ORIGINAL
                    row.outputHash -> AssetState.TRANSLATED
                    else -> AssetState.CONFLICT
                }
            } catch (e: java.util.concurrent.CancellationException) { throw e }
              catch (_: Exception) { AssetState.UNAVAILABLE }
            checkpoint()
            AssetCheck(row.source.relative, state)
        }
        return Diagnostic(journal, assets)
    }

    /** Exercise the replacement primitive only on disposable private files, never on the game. */
    @Synchronized
    fun probe(root: File, scratch: File, checkpoint: () -> Unit = {}) {
        checkpoint()
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val game = root.canonicalFile.toPath()
        val destination = scratch.canonicalFile.toPath()
        require(!game.startsWith(destination) && !destination.startsWith(game))
        require(!Files.isSymbolicLink(scratch.toPath()))
        require(scratch.isDirectory || scratch.mkdirs())
        require(Files.isDirectory(scratch.toPath(), NOFOLLOW_LINKS))
        val sandbox = Files.createTempDirectory(destination, "image-txn-probe-").toFile()
        val file = File(sandbox, "sample.png")
        try {
            val original = byteArrayOf(1, 2, 3)
            val translated = byteArrayOf(4, 5, 6, 7)
            FileOutputStream(file).use { out -> out.write(original); out.fd.sync() }
            val source = ImagePatchArchive.Source(file.name, hash(original), original.size.toLong(), 32, 32)
            val row = ImagePatchArchive.Row(source, hash(translated), translated.size)
            replace(sandbox, row, source.originalHash, row.outputHash, translated, checkpoint)
            replace(sandbox, row, row.outputHash, source.originalHash, original, checkpoint)
            require(parent(sandbox, file.name) { dir, name -> current(dir, name, checkpoint) } == source.originalHash)
        } finally {
            Files.newDirectoryStream(sandbox.toPath()).use { entries ->
                for (entry in entries) Files.deleteIfExists(entry)
            }
            Files.deleteIfExists(sandbox.toPath())
        }
    }

    @Synchronized
    fun status(storage: File, root: File, checkpoint: () -> Unit = {}): Journal =
        readJournal(requireNotNull(ImagePatchBackup.load(storage, root, checkpoint)), checkpoint)

    @Synchronized
    fun apply(storage: File, root: File, patch: File, verify: (ByteArray, ImagePatchArchive.Source) -> Unit,
        checkpoint: () -> Unit = {}): Journal {
        val b = requireNotNull(ImagePatchBackup.load(storage, root, checkpoint))
        val old = readJournal(b, checkpoint)
        require(old.phase == Phase.PREPARED || old.phase == Phase.RESTORED)
        val report = ImagePatchImport.inspect(root, patch, verify, checkpoint)
        require(report.digest == b.patchDigest && report.entries.map { it.row } == b.rows)
        require(report.entries.all { it.state == ImagePatchImport.State.READY })
        for (row in b.rows) parent(root, row.source.relative) { dir, name ->
            require(current(dir, name, checkpoint) == row.source.originalHash)
        }
        try {
            var index = 0
            ImagePatchArchive.inspectPayloads(patch, checkpoint) { row, bytes ->
                require(row == b.rows[index]); verify(bytes, row.source)
                checkpoint()
                writeJournal(b, Journal(Phase.APPLYING, ++index))
                replace(root, row, row.source.originalHash, row.outputHash, bytes, checkpoint)
            }
            require(index == b.rows.size && ImagePatchArchive.digest(patch, checkpoint) == b.patchDigest)
            // Recheck earlier replacements too, not just the last successful move.
            for (row in b.rows) parent(root, row.source.relative) { dir, name ->
                require(current(dir, name, checkpoint) == row.outputHash) { "적용 완료 전 파일이 변경됐습니다." }
            }
            checkpoint()
            val done = Journal(Phase.APPLIED, index)
            writeJournal(b, done)
            return done
        } catch (failure: Exception) {
            // If cancelled or the game starts, checkpoint may refuse recovery; keep the journal for later.
            try { recover(storage, root, checkpoint) } catch (recovery: Exception) { failure.addSuppressed(recovery) }
            throw failure
        }
    }

    private fun originalConflicts(b: ImagePatchBackup.Backup, root: File, checkpoint: () -> Unit): List<String> =
        b.rows.mapNotNull { row ->
            checkpoint()
            val matches = try {
                parent(root, row.source.relative) { dir, name -> current(dir, name, checkpoint) } == row.source.originalHash
            } catch (e: java.util.concurrent.CancellationException) { throw e }
              catch (_: Exception) { false }
            if (matches) null else row.source.relative
        }

    @Synchronized
    fun recover(storage: File, root: File, checkpoint: () -> Unit = {}): Recovery {
        val b = requireNotNull(ImagePatchBackup.load(storage, root, checkpoint))
        val j = readJournal(b, checkpoint)
        // Even with no attempted writes, report unexpected changes rather than claiming success.
        if (j.attempted == 0) return Recovery(0, 0, originalConflicts(b, root, checkpoint))
        var restored = 0; var unchanged = 0
        val conflicts = mutableListOf<String>()
        val originals = File(b.directory, "originals.zip")
        var index = 0
        ImagePatchArchive.inspectPayloads(originals, checkpoint) { original, bytes ->
            val row = b.rows[index++]
            require(original.source == row.source && hash(bytes) == row.source.originalHash)
            if (index <= j.attempted) {
                checkpoint()
                try {
                    val actual = parent(root, row.source.relative) { dir, name -> current(dir, name, checkpoint) }
                    when (actual) {
                        row.source.originalHash -> unchanged++
                        row.outputHash -> { replace(root, row, row.outputHash, row.source.originalHash, bytes, checkpoint); restored++ }
                        else -> conflicts.add(row.source.relative)
                    }
                } catch (e: java.util.concurrent.CancellationException) { throw e }
                  catch (_: Exception) { conflicts.add(row.source.relative) }
            }
        }
        require(index == b.rows.size && ImagePatchArchive.digest(originals, checkpoint) == b.archiveDigest)
        // Never restore outside the attempted prefix. Verify ALL originals before clearing the journal,
        // including files outside that prefix and files restored earlier in this pass.
        val remaining = (conflicts + originalConflicts(b, root, checkpoint)).distinct()
        checkpoint()
        if (remaining.isEmpty()) writeJournal(b, Journal(Phase.RESTORED, 0))
        return Recovery(restored, unchanged, remaining)
    }
}
