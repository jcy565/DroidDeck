package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Explicit private drafts; exact image revision and selected path are part of the identity. */
internal object ImageDraftStore {
    private const val MAGIC = "DDIMAGE1"
    private const val LIMIT = 65536
    data class Identity(val path: String, val digest: String)
    data class Draft(val source: String, val translated: String,
        val script: ImageTranslationInput.Script, val rotation: ImageTranslationInput.Rotation)

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray) = digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun identity(file: File): Identity {
        require(file.extension.lowercase() in ImageTranslationInput.extensions)
        require(!Files.isSymbolicLink(requireNotNull(file.parentFile).toPath()))
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.length() in 1L..ImageTranslationInput.MAX_BYTES.toLong())
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use(ImageTranslationInput::read)
        return Identity(file.canonicalPath, hex(bytes))
    }

    private fun name(identity: Identity): String {
        require(identity.path.isNotBlank() && identity.digest.matches(Regex("[0-9a-f]{64}")))
        return hex(identity.path.toByteArray(Charsets.UTF_8)) + "-" + identity.digest
    }

    private fun location(directory: File, identity: Identity): File {
        require(!Files.isSymbolicLink(directory.toPath()))
        return File(directory, name(identity) + ".draft")
    }

    private fun validate(draft: Draft) {
        ImageTranslationInput.validateText(draft.source)
        if (draft.translated.isNotEmpty()) ImageTranslationInput.validateText(draft.translated)
    }

    @Synchronized
    fun save(directory: File, image: File, expected: Identity, draft: Draft) {
        validate(draft)
        require(identity(image) == expected) { "원본 이미지가 변경됐습니다. 다시 열어 주세요." }
        val file = location(directory, expected)
        require(directory.isDirectory || directory.mkdirs())
        require(!Files.isSymbolicLink(directory.toPath()))
        require(!Files.exists(file.toPath(), NOFOLLOW_LINKS) || Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        val body = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use {
                it.writeUTF(MAGIC)
                it.writeUTF(name(expected))
                it.writeUTF(draft.script.name)
                it.writeUTF(draft.rotation.name)
                it.writeUTF(draft.source)
                it.writeUTF(draft.translated)
            }
        }.toByteArray()
        require(body.size + 32 <= LIMIT)
        val temporary = Files.createTempFile(directory.toPath(), ".image-draft-", ".tmp")
        try {
            temporary.toFile().outputStream().use {
                it.write(digest(body))
                it.write(body)
                it.fd.sync()
            }
            require(identity(image) == expected) { "원본 이미지가 변경됐습니다. 저장을 중단했습니다." }
            Files.move(temporary, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    @Synchronized
    fun load(directory: File, expected: Identity): Draft? {
        val file = location(directory, expected)
        if (!Files.exists(file.toPath(), NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.length() in 33L..LIMIT.toLong())
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= LIMIT)
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        require(bytes.size > 32)
        val body = bytes.copyOfRange(32, bytes.size)
        require(MessageDigest.isEqual(bytes.copyOfRange(0, 32), digest(body))) { "이미지 번역 초안이 손상됐습니다." }
        return DataInputStream(ByteArrayInputStream(body)).use {
            require(it.readUTF() == MAGIC && it.readUTF() == name(expected))
            val script = ImageTranslationInput.Script.valueOf(it.readUTF())
            val rotation = ImageTranslationInput.Rotation.valueOf(it.readUTF())
            val draft = Draft(it.readUTF(), it.readUTF(), script, rotation)
            require(it.read() == -1)
            validate(draft)
            draft
        }
    }
}
