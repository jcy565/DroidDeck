package com.droiddeck.launcher.files

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Explicitly saved drafts are private to the app and bound to an exact source revision. */
internal object TranslationDraftStore {
    private const val LIMIT = TranslationLimits.MAX_DRAFT_BYTES
    private const val MAGIC = "DDTRANSLATION1"

    private fun digest(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun hash(text: String): String = digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun identity(target: String, source: String): String {
        require(target.isNotBlank())
        TranslationJson.validUnicode(target)
        TranslationJson.validUnicode(source)
        return hash(target) + "-" + hash(source)
    }

    private fun validate(source: String, edits: Map<String, String>) {
        val ids = GamePatchAuthor.extract(source).map { it.id }.toSet()
        require(edits.isNotEmpty() && edits.size <= TranslationLimits.MAX_ROWS && edits.keys.all { it in ids })
        // Incomplete translations may be saved; final authoring still validates placeholders.
        require(edits.values.all { it.isNotEmpty() && it.length <= TranslationLimits.MAX_TEXT_CHARS })
        edits.values.forEach { TranslationJson.validUnicode(it) }
    }

    private fun location(directory: File, target: String, source: String): File {
        require(Files.notExists(directory.toPath(), NOFOLLOW_LINKS) ||
            Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS)) { "번역 초안 저장 경로가 안전하지 않습니다." }
        return File(directory, identity(target, source) + ".draft")
    }

    @Synchronized
    fun save(directory: File, target: String, source: String, edits: Map<String, String>) {
        require(edits.size in 1..TranslationLimits.MAX_ROWS)
        val stable = edits.toMap()
        validate(source, stable)
        val file = location(directory, target, source)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeUTF(MAGIC)
            it.writeUTF(identity(target, source))
            it.writeInt(stable.size)
            for ((id, value) in stable.toSortedMap()) {
                it.writeUTF(id)
                it.writeUTF(value)
            }
        }
        val body = buffer.toByteArray()
        require(body.size + 32 <= LIMIT)
        require(directory.isDirectory || directory.mkdirs())
        require(Files.isDirectory(directory.toPath(), NOFOLLOW_LINKS))
        require(Files.notExists(file.toPath(), NOFOLLOW_LINKS) ||
            Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "기존 번역 초안 경로가 안전하지 않습니다." }
        val temporary = Files.createTempFile(directory.toPath(), ".draft-", ".tmp")
        try {
            temporary.toFile().outputStream().use {
                it.write(digest(body))
                it.write(body)
                it.fd.sync()
            }
            Files.move(temporary, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Synchronized
    fun load(directory: File, target: String, source: String): Map<String, String>? {
        val file = location(directory, target, source)
        if (Files.notExists(file.toPath(), NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.length() in 33L..LIMIT.toLong())
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val output = ByteArrayOutputStream()
            val block = ByteArray(4096)
            while (true) {
                val count = input.read(block)
                if (count < 0) break
                require(count > 0 && output.size() + count <= LIMIT)
                output.write(block, 0, count)
            }
            output.toByteArray()
        }
        require(bytes.size > 32)
        val body = bytes.copyOfRange(32, bytes.size)
        require(MessageDigest.isEqual(bytes.copyOfRange(0, 32), digest(body))) { "번역 초안이 손상되었습니다." }
        val edits = DataInputStream(ByteArrayInputStream(body)).use { input ->
            require(input.readUTF() == MAGIC && input.readUTF() == identity(target, source))
            val count = input.readInt()
            require(count in 1..TranslationLimits.MAX_ROWS)
            val result = linkedMapOf<String, String>()
            repeat(count) {
                val id = input.readUTF()
                require(id !in result)
                result[id] = input.readUTF()
            }
            require(input.read() == -1)
            result
        }
        validate(source, edits)
        return edits
    }
}
