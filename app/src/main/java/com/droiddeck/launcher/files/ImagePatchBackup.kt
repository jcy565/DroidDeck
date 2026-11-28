package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest

/** Durable original backup, bound to one root and patch. Never modifies game files. */
internal object ImagePatchBackup {
    private const val MAGIC = "DDIMAGEBACKUP1"
    private const val MAX_RECORD = 262144
    data class Backup(val directory: File, val root: String, val patchDigest: String,
        val archiveDigest: String, val rows: List<ImagePatchArchive.Row>)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun validHash(s: String) = s.matches(Regex("[0-9a-f]{64}"))

    private fun location(storage: File, root: File): File {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val game = root.canonicalFile.toPath()
        val dest = storage.canonicalFile.toPath()
        require(!game.startsWith(dest) && !dest.startsWith(game))
        require(!Files.isSymbolicLink(storage.toPath()))
        return File(storage, hash(root.canonicalPath.toByteArray(Charsets.UTF_8))).also {
            require(!Files.isSymbolicLink(it.toPath()))
        }
    }

    private fun original(root: File, source: ImagePatchArchive.Source, checkpoint: () -> Unit): ByteArray {
        var path = root.canonicalFile.toPath()
        val parts = source.relative.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." && '\\' !in it })
        for ((index, part) in parts.withIndex()) {
            checkpoint()
            path = path.resolve(part)
            require(!Files.isSymbolicLink(path))
            if (index < parts.lastIndex) require(Files.isDirectory(path, NOFOLLOW_LINKS))
        }
        require(Files.isRegularFile(path, NOFOLLOW_LINKS))
        require(source.originalBytes in 1L..ImagePatchArchive.MAX_FILE.toLong())
        require(Files.size(path) == source.originalBytes)
        val data = Files.newInputStream(path, NOFOLLOW_LINKS).use { raw ->
            DataInputStream(raw).use { input ->
                ByteArray(source.originalBytes.toInt()).also { bytes ->
                    var offset = 0
                    while (offset < bytes.size) {
                        checkpoint()
                        val count = minOf(65536, bytes.size - offset)
                        input.readFully(bytes, offset, count)
                        checkpoint()
                        offset += count
                    }
                    require(input.read() == -1)
                }
            }
        }
        require(hash(data) == source.originalHash) { "원본 파일이 변경됐습니다." }
        checkpoint()
        return data
    }

    private fun record(backup: Backup): ByteArray = ByteArrayOutputStream().also { body ->
        DataOutputStream(body).use { out ->
            out.writeUTF(MAGIC); out.writeUTF(backup.root); out.writeUTF(backup.patchDigest)
            out.writeUTF(backup.archiveDigest); out.writeInt(backup.rows.size)
            backup.rows.forEach { row ->
                val s = row.source
                out.writeUTF(s.relative); out.writeUTF(s.originalHash); out.writeLong(s.originalBytes)
                out.writeInt(s.width); out.writeInt(s.height)
                out.writeUTF(row.outputHash); out.writeInt(row.outputBytes)
            }
        }
    }.toByteArray().also { require(it.size <= MAX_RECORD) }

    private fun read(directory: File, expectedRoot: String, checkpoint: () -> Unit): Backup {
        require(Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS))
        val file = File(directory, "recovery.ddib")
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() in 33L..(MAX_RECORD + 32L))
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(MAX_RECORD + 33)
            var size = 0
            while (size < buffer.size) {
                checkpoint()
                val n = input.read(buffer, size, buffer.size - size)
                checkpoint()
                if (n < 0) break
                require(n > 0)
                size += n
            }
            require(size in 33..(MAX_RECORD + 32))
            buffer.copyOf(size)
        }
        val body = bytes.copyOfRange(32, bytes.size)
        require(MessageDigest.isEqual(bytes.copyOfRange(0, 32), MessageDigest.getInstance("SHA-256").digest(body)))
        val backup = DataInputStream(body.inputStream()).use { input ->
            require(input.readUTF() == MAGIC && input.readUTF() == expectedRoot)
            val patch = input.readUTF(); val archive = input.readUTF()
            require(validHash(patch) && validHash(archive))
            val count = input.readInt(); require(count in 1..20)
            val rows = List(count) {
                val s = ImagePatchArchive.Source(input.readUTF(), input.readUTF(), input.readLong(), input.readInt(), input.readInt())
                ImagePatchArchive.Row(s, input.readUTF(), input.readInt()).also {
                    require(validHash(it.outputHash) && it.outputBytes in 1..ImagePatchArchive.MAX_FILE)
                }
            }
            require(input.read() == -1 && rows.sumOf { it.outputBytes.toLong() } <= ImagePatchArchive.MAX_TOTAL)
            Backup(directory, expectedRoot, patch, archive, rows)
        }
        val originals = File(directory, "originals.zip")
        require(ImagePatchArchive.digest(originals, checkpoint) == backup.archiveDigest)
        val rows = ImagePatchArchive.verify(originals, checkpoint)
        require(rows == backup.rows.map { ImagePatchArchive.Row(it.source, it.source.originalHash, it.source.originalBytes.toInt()) })
        require(ImagePatchArchive.digest(originals, checkpoint) == backup.archiveDigest)
        checkpoint()
        return backup
    }

    @Synchronized
    fun load(storage: File, root: File, checkpoint: () -> Unit = {}): Backup? {
        val directory = location(storage, root)
        if (Files.notExists(directory.toPath(), NOFOLLOW_LINKS)) return null
        return read(directory, root.canonicalPath, checkpoint)
    }

    @Synchronized
    fun create(storage: File, root: File, report: ImagePatchImport.Report,
        verify: (ByteArray, ImagePatchArchive.Source) -> Unit, checkpoint: () -> Unit = {}): Backup {
        checkpoint()
        val destination = location(storage, root)
        require(Files.notExists(destination.toPath(), NOFOLLOW_LINKS)) { "이미 원본 백업이 있습니다. 기존 백업은 덮어쓰지 않습니다." }
        val fresh = ImagePatchImport.inspect(root, report.file, verify, checkpoint)
        require(fresh == report && fresh.entries.all { it.state == ImagePatchImport.State.READY }) {
            "모든 패치 대상이 원본과 일치해야 백업할 수 있습니다."
        }
        val rows = fresh.entries.map { it.row }
        require(rows.sumOf { it.source.originalBytes } <= ImagePatchArchive.MAX_TOTAL)
        require(storage.isDirectory || storage.mkdirs())
        require(Files.isDirectory(storage.toPath(), NOFOLLOW_LINKS))
        val temporary = Files.createTempDirectory(storage.toPath(), ".backup-").toFile()
        try {
            val archive = ImagePatchArchive.create(root, temporary, rows.map { it.source }, { source ->
                original(root, source, checkpoint)
            }, checkpoint)
            Files.move(archive.file.toPath(), File(temporary, "originals.zip").toPath(), ATOMIC_MOVE)
            val value = Backup(temporary, root.canonicalPath, report.digest, archive.digest, rows)
            val body = record(value)
            FileOutputStream(File(temporary, "recovery.ddib")).use {
                it.write(MessageDigest.getInstance("SHA-256").digest(body)); it.write(body); it.fd.sync()
            }
            require(read(temporary, value.root, checkpoint) == value)
            require(ImagePatchImport.inspect(root, report.file, verify, checkpoint) == fresh)
            checkpoint()
            require(Files.notExists(destination.toPath(), NOFOLLOW_LINKS))
            Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE)
            return value.copy(directory = destination)
        } finally {
            // Only files in our own newly created staging directory are removed.
            if (Files.isDirectory(temporary.toPath(), NOFOLLOW_LINKS)) {
                Files.newDirectoryStream(temporary.toPath()).use { entries ->
                    for (entry in entries) Files.deleteIfExists(entry)
                }
                Files.deleteIfExists(temporary.toPath())
            }
        }
    }
}
