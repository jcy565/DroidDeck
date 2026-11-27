package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** One explicitly saved queue per canonical root, in app-private no-backup storage. */
internal object ImageQueueStore {
    private const val MAGIC = "DDIMAGEQUEUE1"
    private const val LIMIT = 128 * 1024

    private fun rootPath(root: File): String {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        return root.canonicalPath.also { require(it.length <= 4096) }
    }

    private fun location(directory: File, root: String): File {
        require(!Files.isSymbolicLink(directory.toPath()))
        val key = MessageDigest.getInstance("SHA-256").digest(root.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return File(directory, "$key.queue")
    }

    private fun validate(c: ImageAssetDiscovery.Candidate) {
        require(c.relative.length in 1..4096 && c.relative.split('/').all {
            it.isNotBlank() && it != "." && it != ".." && '\\' !in it
        })
        require(File(c.relative).extension.lowercase() in ImageTranslationInput.extensions)
        require(!File(c.relative).name.startsWith("dd-translated-", ignoreCase = true))
        require(c.bytes in 1L..ImageTranslationInput.MAX_BYTES.toLong())
        require(c.digest.matches(Regex("[0-9a-f]{64}")))
        ImageTranslationInput.sampleSize(c.width, c.height)
    }

    @Synchronized
    fun save(directory: File, queue: ImageAssetDiscovery.Queue) {
        val root = rootPath(queue.root)
        require(queue.candidates.size in 1..20 && queue.candidates.map { it.relative }.distinct().size == queue.candidates.size)
        queue.candidates.forEach(::validate)
        val body = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF(MAGIC)
                out.writeUTF(root)
                out.writeInt(queue.candidates.size)
                queue.candidates.forEach { c ->
                    out.writeUTF(c.relative); out.writeInt(c.width); out.writeInt(c.height)
                    out.writeLong(c.bytes); out.writeUTF(c.digest)
                }
            }
        }.toByteArray()
        require(body.size <= LIMIT)
        require(directory.isDirectory || directory.mkdirs())
        val destination = location(directory, root)
        require(!Files.exists(destination.toPath(), NOFOLLOW_LINKS) || Files.isRegularFile(destination.toPath(), NOFOLLOW_LINKS))
        val temp = Files.createTempFile(directory.toPath(), "queue-", ".tmp").toFile()
        try {
            FileOutputStream(temp).use { it.write(body); it.fd.sync() }
            Files.move(temp.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp.toPath()) }
    }

    /** Keep existing order and never silently accept a changed source revision. */
    fun combine(existing: ImageAssetDiscovery.Queue?, incoming: ImageAssetDiscovery.Queue): ImageAssetDiscovery.Queue {
        val root = rootPath(incoming.root)
        if (existing != null) require(rootPath(existing.root) == root) { "서로 다른 폴더의 목록은 합칠 수 없습니다." }
        val combined = linkedMapOf<String, ImageAssetDiscovery.Candidate>()
        for (candidate in existing?.candidates.orEmpty() + incoming.candidates) {
            validate(candidate)
            val previous = combined[candidate.relative]
            require(previous == null || previous == candidate) {
                "이미 저장한 원본과 달라진 파일이 있습니다. 기존 목록은 변경하지 않았습니다."
            }
            combined[candidate.relative] = candidate
            require(combined.size <= 20) { "작업 목록은 최대 20개입니다. 기존 목록은 변경하지 않았습니다." }
        }
        return ImageAssetDiscovery.Queue(File(root), combined.values.toList())
    }

    /** Serialize load/merge/save so a failed merge leaves the existing record intact. */
    @Synchronized
    fun merge(directory: File, incoming: ImageAssetDiscovery.Queue): ImageAssetDiscovery.Queue {
        val merged = combine(load(directory, incoming.root), incoming)
        save(directory, merged)
        return merged
    }

    @Synchronized
    fun load(directory: File, root: File): ImageAssetDiscovery.Queue? {
        val path = rootPath(root)
        val file = location(directory, path)
        if (!Files.exists(file.toPath(), NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.length() in 1L..LIMIT.toLong())
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                require(out.size() + n <= LIMIT)
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        }
        return DataInputStream(bytes.inputStream()).use { input ->
            require(input.readUTF() == MAGIC && input.readUTF() == path)
            val count = input.readInt()
            require(count in 1..20)
            val entries = List(count) {
                ImageAssetDiscovery.Candidate(input.readUTF(), input.readInt(), input.readInt(),
                    input.readLong(), input.readUTF()).also(::validate)
            }
            require(input.read() == -1)
            ImageAssetDiscovery.Queue(File(path), entries)
        }
    }
}
