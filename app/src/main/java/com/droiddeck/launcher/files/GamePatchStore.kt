package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

internal object GamePatchStore {
    private const val LIMIT = 1024 * 1024
    private val extensions = setOf("ini", "cfg", "json", "xml", "csv", "tsv", "txt", "po", "yaml", "yml")
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

    private fun read(file: File): ByteArray = file.inputStream().use {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = it.read(buffer)
            if (n < 0) break
            require(out.size() + n <= LIMIT) { "File too large" }
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }

    private fun replace(file: File, expected: ByteArray, bytes: ByteArray) {
        require(bytes.size <= LIMIT)
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "Invalid target file" }
        val temp = Files.createTempFile(file.parentFile!!.toPath(), ".ddpatch-", ".tmp")
        try {
            Files.write(temp, bytes)
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
        java.io.DataOutputStream(backup.outputStream()).use {
            it.writeUTF(root.canonicalPath)
            it.writeUTF(relative)
            it.writeUTF(hash(proposed))
            it.writeInt(expected.size)
            it.write(expected)
            it.flush()
        }
        replace(file, expected, proposed)
        return backup
    }

    @Synchronized
    fun restore(root: File, backup: File) {
        require(Files.isRegularFile(backup.toPath(), NOFOLLOW_LINKS)) { "Invalid backup file" }
        require(backup.length() <= LIMIT + 196608)
        val record = java.io.DataInputStream(backup.inputStream()).use {
            require(it.readUTF() == root.canonicalPath)
            val relative = it.readUTF()
            val applied = it.readUTF()
            val size = it.readInt()
            require(size in 0..LIMIT)
            val original = ByteArray(size)
            it.readFully(original)
            require(it.read() == -1)
            Backup(relative, original, applied)
        }
        val file = target(root, record.relativePath)
        val current = read(file)
        require(hash(current) == record.appliedHash) { "File changed since patch" }
        replace(file, current, record.original)
    }
}
