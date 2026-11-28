package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal object GamePatchStore {
    private const val LIMIT = 1024 * 1024
    private const val BACKUP_LIMIT = LIMIT + 196608
    private const val CHECKSUM_BYTES = 32
    val extensions = setOf("ini", "cfg", "json", "xml", "csv", "tsv", "txt", "po", "yaml", "yml")
    data class Backup(val relativePath: String, val original: ByteArray, val appliedHash: String)
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hash(bytes: ByteArray) = digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun target(root: File, relative: String): File {
        require(root.isDirectory && !Files.isSymbolicLink(root.toPath()))
        require(relative.isNotBlank() && !File(relative).isAbsolute)
        val parts = relative.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." || '\\' in it })
        var file = root.canonicalFile
        for (part in parts) {
            file = File(file, part)
            require(!Files.isSymbolicLink(file.toPath()))
        }
        require(file.extension.lowercase() in extensions)
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        return file
    }

    fun readLog(file: File): ByteArray {
        require(file.extension.equals("log", true) && Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        return read(file)
    }

    fun readTarget(file: File): ByteArray = read(target(file.parentFile!!, file.name))

    private fun read(file: File, checkpoint: () -> Unit = {}): ByteArray =
        Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { readLimited(it, checkpoint) }

    fun readLimited(input: java.io.InputStream, checkpoint: () -> Unit = {}): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            checkpoint()
            val n = input.read(buffer)
            checkpoint()
            if (n < 0) break
            require(n > 0) { "Input stream made no progress" }
            require(out.size() + n <= LIMIT) { "File too large" }
            out.write(buffer, 0, n)
        }
        checkpoint()
        return out.toByteArray()
    }

    private fun replace(file: File, expected: ByteArray, bytes: ByteArray, checkWrite: () -> Unit) {
        require(bytes.size <= LIMIT)
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "Invalid target file" }
        val temp = Files.createTempFile(file.parentFile!!.toPath(), ".ddpatch-", ".tmp")
        try {
            java.io.FileOutputStream(temp.toFile()).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (Files.getFileStore(file.toPath()).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(file.toPath(), NOFOLLOW_LINKS))
            }
            require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "Invalid target file" }
            require(read(temp.toFile()).contentEquals(bytes)) { "Temporary patch verification failed" }
            require(read(file).contentEquals(expected)) { "File changed; preview again" }
            checkWrite()
            Files.move(temp, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    @Synchronized
    fun apply(root: File, relative: String, expected: ByteArray, proposed: ByteArray, backupDir: File,
              checkWrite: () -> Unit = {}): File {
        checkWrite()
        require(expected.size <= LIMIT && proposed.size <= LIMIT)
        val original = expected.copyOf()
        val replacement = proposed.copyOf()
        val file = target(root, relative)
        require(read(file).contentEquals(original)) { "File changed; preview again" }
        require(Files.notExists(backupDir.toPath(), NOFOLLOW_LINKS) ||
            Files.isDirectory(backupDir.toPath(), NOFOLLOW_LINKS)) { "Invalid backup directory" }
        val gamePath = root.canonicalFile.toPath()
        val storagePath = backupDir.canonicalFile.toPath()
        require(!gamePath.startsWith(storagePath) && !storagePath.startsWith(gamePath)) {
            "Backup storage must be separate from the game directory"
        }
        require(backupDir.isDirectory || backupDir.mkdirs())
        require(Files.isDirectory(backupDir.toPath(), NOFOLLOW_LINKS))
        val backup = Files.createTempFile(backupDir.toPath(), "ddpatch-", ".bak").toFile()
        try {
            val body = java.io.ByteArrayOutputStream().also { buffer ->
                java.io.DataOutputStream(buffer).use { record ->
                    record.writeUTF("DDPATCH3")
                    record.writeUTF(hash(original))
                    record.writeUTF(root.canonicalPath)
                    record.writeUTF(relative)
                    record.writeUTF(hash(replacement))
                    record.writeInt(original.size)
                    record.write(original)
                }
            }.toByteArray()
            require(body.size + CHECKSUM_BYTES <= BACKUP_LIMIT)
            backup.outputStream().use { raw ->
                raw.write(body)
                raw.write(digest(body))
                raw.fd.sync()
            }
            val checked = readBackup(root, backup)
            require(checked.relativePath == relative && checked.original.contentEquals(original) &&
                checked.appliedHash == hash(replacement)) { "Backup verification failed" }
        } catch (e: Exception) {
            backup.delete()
            throw e
        }
        replace(file, original, replacement) {
            try {
                checkWrite()
            } catch (e: Exception) {
                Files.deleteIfExists(backup.toPath())
                throw e
            }
        }
        return backup
    }

    data class History(val restorable: File?, val status: String)

    fun findMatchingBackup(root: File, relative: String, backupDir: File): File? =
        history(root, relative, backupDir).restorable

    @Synchronized
    fun history(root: File, relative: String, backupDir: File,
                checkpoint: () -> Unit = {}): History {
        checkpoint()
        val file = target(root, relative)
        val currentHash = hash(read(file, checkpoint))
        checkpoint()
        if (!backupDir.isDirectory || Files.isSymbolicLink(backupDir.toPath())) return History(null, "")
        val candidates = Files.newDirectoryStream(backupDir.toPath()).use { entries ->
            entries.mapNotNull { path ->
                checkpoint()
                val candidate = path.toFile()
                candidate.takeIf { it.name.startsWith("ddpatch-") && it.extension == "bak" &&
                    Files.isRegularFile(path, NOFOLLOW_LINKS) && it.length() <= BACKUP_LIMIT }
            }
        }.sortedByDescending { it.lastModified() }
        var status = ""
        for (candidate in candidates) {
            checkpoint()
            // A cancelled or budget-blocked read is not a corrupt backup.
            var checkpointFailure: Exception? = null
            val record = try {
                readBackup(root, candidate) {
                    try { checkpoint() } catch (e: Exception) {
                        checkpointFailure = e
                        throw e
                    }
                }
            } catch (e: Exception) {
                checkpointFailure?.let { throw it }
                if (e is java.util.concurrent.CancellationException) throw e
                continue
            }
            checkpoint()
            if (record.relativePath != relative) continue
            if (status.isEmpty()) status = when (currentHash) {
                record.appliedHash -> "파일 내용이 최근 적용본과 일치합니다."
                hash(record.original) -> "파일 내용이 최근 변경 전 원본과 일치합니다."
                else -> "최근 적용 뒤 파일 내용이 바뀌었습니다. 변경 주체와 실행 시 적용값은 확인되지 않았습니다."
            }
            if (currentHash == record.appliedHash) {
                checkpoint()
                // Do not offer restoration against an already changed source snapshot.
                if (hash(read(file, checkpoint)) != currentHash) return History(null, "파일이 조회 중 변경됐습니다. 다시 확인하세요.")
                checkpoint()
                return History(candidate, status)
            }
        }
        checkpoint()
        return History(null, status)
    }

    private fun readBackup(root: File, backup: File, checkpoint: () -> Unit = {}): Backup {
        checkpoint()
        require(Files.isRegularFile(backup.toPath(), NOFOLLOW_LINKS)) { "Invalid backup file" }
        require(backup.length() in 1L..BACKUP_LIMIT.toLong())
        val bytes = Files.newInputStream(backup.toPath(), NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
            while (true) {
                checkpoint()
                val count = input.read(buffer)
                checkpoint()
                if (count < 0) break
                require(count > 0 && out.size() + count <= BACKUP_LIMIT)
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        val marker = java.io.DataInputStream(bytes.inputStream()).use { it.readUTF() }
        val body = if (marker == "DDPATCH3") {
            require(bytes.size > CHECKSUM_BYTES)
            val end = bytes.size - CHECKSUM_BYTES
            bytes.copyOfRange(0, end).also {
                require(MessageDigest.isEqual(digest(it), bytes.copyOfRange(end, bytes.size))) {
                    "Damaged backup metadata or contents"
                }
            }
        } else bytes
        return java.io.DataInputStream(body.inputStream()).use {
            val first = it.readUTF()
            val versioned = first == "DDPATCH2" || first == "DDPATCH3"
            val digest = if (versioned) it.readUTF() else null
            val path = if (versioned) it.readUTF() else first
            require(path == root.canonicalPath)
            val relative = it.readUTF()
            val applied = it.readUTF()
            require(applied.matches(Regex("[0-9a-f]{64}")))
            val size = it.readInt()
            require(size in 0..LIMIT)
            val original = ByteArray(size)
            it.readFully(original)
            require(it.read() == -1)
            require(digest == null || digest == hash(original)) { "Damaged backup" }
            checkpoint()
            Backup(relative, original, applied)
        }
    }

    @Synchronized
    fun restore(root: File, backup: File, selected: String? = null, checkWrite: () -> Unit = {}) {
        checkWrite()
        val record = readBackup(root, backup)
        require(selected == null || selected == record.relativePath) { "Backup target changed" }
        val file = target(root, record.relativePath)
        val current = read(file)
        require(hash(current) == record.appliedHash) { "File changed since patch" }
        replace(file, current, record.original, checkWrite)
    }
}
