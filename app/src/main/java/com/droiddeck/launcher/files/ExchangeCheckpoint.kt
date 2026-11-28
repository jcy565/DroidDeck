package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.UUID

/** Explicit local checkpoint; no source text, target path, draft text, prompt or response stored. */
internal object ExchangeCheckpoint {
    private const val LEGACY_MAGIC = 0x44445831
    private const val MAGIC = 0x44445832
    private const val DIGEST_BYTES = 32
    private const val MAX_BYTES = 8192L
    data class Saved(val id: String, val instruction: String, val start: Int, val fingerprint: String)

    private fun canonicalId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    private fun validate(saved: Saved) {
        require(canonicalId(saved.id) && saved.instruction.isNotBlank() && saved.instruction.length <= 1000)
        require(Charsets.UTF_8.newEncoder().canEncode(saved.instruction)) { "Invalid instruction Unicode" }
        require(saved.start in 0..500 && saved.fingerprint.matches(Regex("[a-f0-9]{64}")))
    }

    fun fingerprint(target: String, source: String, edits: Map<String, String>, prompt: String): String {
        val stable = edits.toMap()
        val digest = MessageDigest.getInstance("SHA-256")
        val encoder = Charsets.UTF_8.newEncoder()
        fun add(value: String) {
            require(encoder.canEncode(value)) { "Invalid fingerprint Unicode" }
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        add("DroidDeck exchange checkpoint 1")
        add(target); add(source); add(prompt); add(stable.size.toString())
        stable.toSortedMap().forEach { (key, value) -> add(key); add(value) }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun directory(file: File): java.nio.file.Path {
        val path = requireNotNull(file.parentFile).toPath()
        require(Files.notExists(path, NOFOLLOW_LINKS) || Files.isDirectory(path, NOFOLLOW_LINKS)) { "Invalid checkpoint directory" }
        return path
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

    @Synchronized fun save(file: File, saved: Saved) {
        validate(saved)
        val directory = directory(file)
        require(Files.notExists(file.toPath(), NOFOLLOW_LINKS) || Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        val body = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC); out.writeUTF(saved.id); out.writeUTF(saved.instruction)
                out.writeInt(saved.start); out.writeUTF(saved.fingerprint)
            }
        }.toByteArray()
        require(body.size + DIGEST_BYTES <= MAX_BYTES)
        Files.createDirectories(directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
        val temp = Files.createTempFile(directory, ".exchange-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { stream ->
                stream.write(body); stream.write(digest(body)); stream.fd.sync()
            }
            require(Files.size(temp) <= MAX_BYTES)
            Files.move(temp, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    @Synchronized fun load(file: File): Saved? {
        directory(file)
        val path = file.toPath()
        if (Files.notExists(path, NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) in 1..MAX_BYTES)
        val bytes = Files.newInputStream(path, NOFOLLOW_LINKS).use { input ->
            val out = ByteArrayOutputStream()
            val block = ByteArray(4096)
            while (true) {
                val n = input.read(block)
                if (n < 0) break
                require(n > 0 && out.size() + n <= MAX_BYTES)
                out.write(block, 0, n)
            }
            out.toByteArray()
        }
        val magic = DataInputStream(bytes.inputStream()).use { it.readInt() }
        val body = when (magic) {
            LEGACY_MAGIC -> bytes
            MAGIC -> {
                require(bytes.size > DIGEST_BYTES)
                val end = bytes.size - DIGEST_BYTES
                bytes.copyOfRange(0, end).also {
                    require(MessageDigest.isEqual(digest(it), bytes.copyOfRange(end, bytes.size))) { "Damaged checkpoint" }
                }
            }
            else -> error("Unsupported checkpoint format")
        }
        return DataInputStream(body.inputStream()).use { input ->
            require(input.readInt() == magic)
            val saved = Saved(input.readUTF(), input.readUTF(), input.readInt(), input.readUTF())
            validate(saved); require(input.read() == -1)
            saved
        }
    }

    /** Explicit user discard only; never used for automatic response cleanup. */
    @Synchronized fun discard(file: File): Boolean {
        directory(file)
        val path = file.toPath()
        if (Files.notExists(path, NOFOLLOW_LINKS)) return false
        require(Files.isRegularFile(path, NOFOLLOW_LINKS)) { "Invalid checkpoint path" }
        return Files.deleteIfExists(path)
    }

    @Synchronized fun clear(file: File, id: String): Boolean {
        val saved = load(file) ?: return false
        return saved.id == id && Files.deleteIfExists(file.toPath())
    }
}
