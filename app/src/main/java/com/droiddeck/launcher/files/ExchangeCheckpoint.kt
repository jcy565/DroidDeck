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
    private const val MAGIC = 0x44445831
    private const val MAX_BYTES = 8192L
    data class Saved(val id: String, val instruction: String, val start: Int, val fingerprint: String)

    private fun canonicalId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    private fun validate(saved: Saved) {
        require(canonicalId(saved.id) && saved.instruction.isNotBlank() && saved.instruction.length <= 1000)
        require(saved.start in 0..500 && saved.fingerprint.matches(Regex("[a-f0-9]{64}")))
    }

    fun fingerprint(target: String, source: String, edits: Map<String, String>, prompt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun add(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        add("DroidDeck exchange checkpoint 1")
        add(target); add(source); add(prompt); add(edits.size.toString())
        edits.toSortedMap().forEach { (key, value) -> add(key); add(value) }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Synchronized fun save(file: File, saved: Saved) {
        validate(saved)
        val directory = requireNotNull(file.parentFile).toPath()
        Files.createDirectories(directory)
        require(!Files.isSymbolicLink(directory) && !Files.isSymbolicLink(file.toPath()))
        val temp = Files.createTempFile(directory, ".exchange-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(MAGIC); out.writeUTF(saved.id); out.writeUTF(saved.instruction)
                out.writeInt(saved.start); out.writeUTF(saved.fingerprint); out.flush(); stream.fd.sync()
            }
            require(Files.size(temp) <= MAX_BYTES)
            Files.move(temp, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    @Synchronized fun load(file: File): Saved? {
        require(!Files.isSymbolicLink(requireNotNull(file.parentFile).toPath()))
        val path = file.toPath()
        if (Files.notExists(path, NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) in 1..MAX_BYTES)
        return DataInputStream(Files.newInputStream(path, NOFOLLOW_LINKS)).use { input ->
            require(input.readInt() == MAGIC)
            val saved = Saved(input.readUTF(), input.readUTF(), input.readInt(), input.readUTF())
            validate(saved); require(input.read() == -1)
            saved
        }
    }

    /** Explicit user discard only; never used for automatic response cleanup. */
    @Synchronized fun discard(file: File): Boolean {
        require(!Files.isSymbolicLink(requireNotNull(file.parentFile).toPath()))
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
