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
    private const val REGION_MAGIC = "DDIMAGE2"
    private const val LIMIT = 65536
    data class Identity(val path: String, val digest: String)
    data class Draft(val source: String, val translated: String,
        val script: ImageTranslationInput.Script, val rotation: ImageTranslationInput.Rotation,
        val regions: ImageTextRegions.Result? = null, val edits: Map<Int, String> = emptyMap())

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
        val regions = draft.regions
        if (regions == null) {
            require(draft.edits.isEmpty())
            return
        }
        require(draft.rotation == ImageTranslationInput.Rotation.NONE && regions.text == draft.source)
        val checked = ImageTextRegions.build(regions.text, regions.width, regions.height,
            regions.regions.asSequence().map { it.box to it.text })
        require(checked.regions == regions.regions && !checked.partial)
        if (draft.edits.isNotEmpty()) ImageTextRegions.summary(regions, draft.edits)
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
                it.writeUTF(if (draft.regions == null) MAGIC else REGION_MAGIC)
                it.writeUTF(name(expected))
                it.writeUTF(draft.script.name)
                it.writeUTF(draft.rotation.name)
                it.writeUTF(draft.source)
                it.writeUTF(draft.translated)
                draft.regions?.let { result ->
                    it.writeInt(result.width)
                    it.writeInt(result.height)
                    it.writeBoolean(result.partial)
                    it.writeInt(result.regions.size)
                    result.regions.forEach { region ->
                        it.writeInt(region.id)
                        it.writeInt(region.box.left)
                        it.writeInt(region.box.top)
                        it.writeInt(region.box.right)
                        it.writeInt(region.box.bottom)
                        it.writeUTF(region.text)
                        it.writeUTF(draft.edits[region.id].orEmpty())
                    }
                }
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
            val magic = it.readUTF()
            require(magic in setOf(MAGIC, REGION_MAGIC) && it.readUTF() == name(expected))
            val script = ImageTranslationInput.Script.valueOf(it.readUTF())
            val rotation = ImageTranslationInput.Rotation.valueOf(it.readUTF())
            val source = it.readUTF()
            val translated = it.readUTF()
            val draft = if (magic == REGION_MAGIC) {
                val width = it.readInt()
                val height = it.readInt()
                val partial = it.readBoolean()
                val count = it.readInt()
                require(count in 0..ImageTextRegions.MAX_REGIONS)
                val edits = linkedMapOf<Int, String>()
                val regions = List(count) { _ ->
                    val id = it.readInt()
                    val box = ImageTextRegions.Box(it.readInt(), it.readInt(), it.readInt(), it.readInt())
                    val text = it.readUTF()
                    val edit = it.readUTF()
                    if (edit.isNotEmpty()) edits[id] = edit
                    ImageTextRegions.Region(id, box, text)
                }
                Draft(source, translated, script, rotation,
                    ImageTextRegions.Result(source, width, height, regions, partial), edits.toMap())
            } else Draft(source, translated, script, rotation)
            require(it.read() == -1)
            validate(draft)
            draft
        }
    }
}
