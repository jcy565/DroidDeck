package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal object GamePatchStore {
    private const val LIMIT = 1024 * 1024
    val extensions = setOf("ini", "cfg", "json", "xml", "csv", "tsv", "txt", "po", "yaml", "yml")
    data class Backup(val relativePath: String, val original: ByteArray, val appliedHash: String)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

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

    private fun read(file: File): ByteArray = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { readLimited(it) }

    fun readLimited(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= LIMIT) { "File too large" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun replace(file: File, expected: ByteArray, bytes: ByteArray) {
        require(bytes.size <= LIMIT)
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "Invalid target file" }
        val temp = Files.createTempFile(file.parentFile!!.toPath(), ".ddpatch-", ".tmp")
        try {
            Files.write(temp, bytes)
            if (Files.getFileStore(file.toPath()).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(file.toPath(), NOFOLLOW_LINKS))
            }
            require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "Invalid target file" }
            require(read(file).contentEquals(expected)) { "File changed; preview again" }
            Files.move(temp, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    @Synchronized
    fun apply(root: File, relative: String, expected: ByteArray, proposed: ByteArray, backupDir: File): File {
        require(expected.size <= LIMIT && proposed.size <= LIMIT)
        val file = target(root, relative)
        require(read(file).contentEquals(expected)) { "File changed; preview again" }
        require(backupDir.isDirectory || backupDir.mkdirs())
        require(!Files.isSymbolicLink(backupDir.toPath()))
        val backup = Files.createTempFile(backupDir.toPath(), "ddpatch-", ".bak").toFile()
        try {
            backup.outputStream().use { raw ->
                val record = java.io.DataOutputStream(raw)
                record.writeUTF("DDPATCH2")
                record.writeUTF(hash(expected))
                record.writeUTF(root.canonicalPath)
                record.writeUTF(relative)
                record.writeUTF(hash(proposed))
                record.writeInt(expected.size)
                record.write(expected)
                record.flush()
                raw.fd.sync()
            }
            require(readBackup(root, backup).original.contentEquals(expected)) { "Backup verification failed" }
        } catch (e: Exception) {
            backup.delete()
            throw e
        }
        replace(file, expected, proposed)
        return backup
    }

    data class History(val restorable: File?, val status: String)

    fun findMatchingBackup(root: File, relative: String, backupDir: File): File? =
        history(root, relative, backupDir).restorable

    @Synchronized
    fun history(root: File, relative: String, backupDir: File): History {
        val file = target(root, relative)
        val currentHash = hash(read(file))
        if (!backupDir.isDirectory || Files.isSymbolicLink(backupDir.toPath())) return History(null, "")
        val candidates = backupDir.listFiles().orEmpty()
            .filter { it.name.startsWith("ddpatch-") && it.extension == "bak" &&
                Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) && it.length() <= LIMIT + 196608 }
            .sortedByDescending { it.lastModified() }
        var status = ""
        for (candidate in candidates) {
            val record = runCatching { readBackup(root, candidate) }.getOrNull() ?: continue
            if (record.relativePath != relative) continue
            if (status.isEmpty()) status = when (currentHash) {
                record.appliedHash -> "파일 내용이 최근 적용본과 일치합니다."
                hash(record.original) -> "파일 내용이 최근 변경 전 원본과 일치합니다."
                else -> "최근 적용 뒤 파일 내용이 바뀌었습니다. 변경 주체와 실행 시 적용값은 확인되지 않았습니다."
            }
            if (currentHash == record.appliedHash) return History(candidate, status)
        }
        return History(null, status)
    }

    private fun readBackup(root: File, backup: File): Backup {
        require(Files.isRegularFile(backup.toPath(), NOFOLLOW_LINKS)) { "Invalid backup file" }
        require(backup.length() <= LIMIT + 196608)
        return java.io.DataInputStream(Files.newInputStream(backup.toPath(), NOFOLLOW_LINKS)).use {
            val first = it.readUTF()
            val versioned = first == "DDPATCH2"
            val digest = if (versioned) it.readUTF() else null
            val path = if (versioned) it.readUTF() else first
            require(path == root.canonicalPath)
            val relative = it.readUTF()
            val applied = it.readUTF()
            val size = it.readInt()
            require(size in 0..LIMIT)
            val original = ByteArray(size)
            it.readFully(original)
            require(it.read() == -1)
            require(digest == null || digest == hash(original)) { "Damaged backup" }
            Backup(relative, original, applied)
        }
    }

    @Synchronized
    fun restore(root: File, backup: File, selected: String? = null) {
        val record = readBackup(root, backup)
        require(selected == null || selected == record.relativePath) { "Backup target changed" }
        val file = target(root, record.relativePath)
        val current = read(file)
        require(hash(current) == record.appliedHash) { "File changed since patch" }
        replace(file, current, record.original)
    }
}
