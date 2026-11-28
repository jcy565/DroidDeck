package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.Locale

/** Explicit private save; candidates remain revision-bound and are rechecked on opening. */
internal object TranslationQueueStore {
    private const val MAGIC = "DDTEXTQUEUE1"
    private const val MAX = 256 * 1024
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun location(directory: File, root: File): Pair<String, File> {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val game = root.canonicalFile.toPath()
        val target = directory.canonicalFile.toPath()
        require(!game.startsWith(target) && !target.startsWith(game))
        require(!Files.isSymbolicLink(directory.toPath()))
        val path = game.toString()
        require(path.length <= 4096)
        val key = hash(path.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        return path to File(directory, "$key.queue")
    }
    private fun validate(c: TranslationDiscovery.Candidate) {
        require(c.relative.length in 1..4096 && c.relative.split('/').all {
            it.isNotBlank() && it != "." && it != ".." && it.none { ch -> ch.isISOControl() || ch == '\\' }
        })
        require(File(c.relative).extension.lowercase(Locale.ROOT) in setOf("tsv", "csv", "json"))
        require(c.rows in 1..TranslationLimits.MAX_ROWS && c.digest.matches(Regex("[0-9a-f]{64}")))
    }

    @Synchronized
    fun save(directory: File, queue: TranslationWorklist.Queue, checkpoint: () -> Unit = {}) {
        val (root, destination) = location(directory, queue.root)
        queue.files.forEach { checkpoint(); validate(it); TranslationDiscovery.snapshot(queue.root, it) }
        val body = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { out ->
                out.writeUTF(MAGIC); out.writeUTF(root); out.writeInt(queue.files.size)
                queue.files.forEach { c ->
                    out.writeUTF(c.relative); out.writeInt(c.rows); out.writeUTF(c.digest)
                }
            }
        }.toByteArray()
        require(body.size <= MAX)
        require(directory.isDirectory || directory.mkdirs())
        require(Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS))
        require(Files.notExists(destination.toPath(), NOFOLLOW_LINKS) ||
            Files.isRegularFile(destination.toPath(), NOFOLLOW_LINKS))
        val temp = Files.createTempFile(directory.toPath(), ".text-queue-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { it.write(hash(body)); it.write(body); it.fd.sync() }
            checkpoint()
            Files.move(temp, destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    @Synchronized
    fun load(directory: File, root: File, checkpoint: () -> Unit = {}): TranslationWorklist.Queue? {
        val (path, file) = location(directory, root)
        if (Files.notExists(file.toPath(), NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() in 33L..(MAX + 32L))
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                checkpoint()
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= MAX + 32)
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        require(bytes.size > 32)
        val body = bytes.copyOfRange(32, bytes.size)
        require(MessageDigest.isEqual(bytes.copyOfRange(0, 32), hash(body)))
        return DataInputStream(body.inputStream()).use { input ->
            require(input.readUTF() == MAGIC && input.readUTF() == path)
            val count = input.readInt()
            require(count in 1..20)
            val files = List(count) {
                TranslationDiscovery.Candidate(input.readUTF(), input.readInt(), input.readUTF()).also(::validate)
            }
            require(input.read() == -1)
            checkpoint()
            TranslationWorklist.Queue(File(path), files)
        }
    }
}
