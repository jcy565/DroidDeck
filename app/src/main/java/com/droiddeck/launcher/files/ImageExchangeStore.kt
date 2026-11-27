package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Save only a token and a fingerprint; rebuild and verify the exact request on restore. */
internal object ImageExchangeStore {
    private const val MAGIC = "DDIMAGEEXCHANGE1"
    private const val LIMIT = 512

    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun location(directory: File, root: File): File {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        require(!Files.isSymbolicLink(directory.toPath()))
        return File(directory, hash(root.canonicalPath.toByteArray(Charsets.UTF_8)) + ".exchange")
    }

    private fun fingerprint(request: ImageBatchExchange.Request): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeUTF(request.json)
            out.writeInt(request.targets.size)
            for (target in request.targets) {
                val c = target.candidate
                val d = target.draft
                out.writeUTF(c.relative); out.writeInt(c.width); out.writeInt(c.height); out.writeLong(c.bytes)
                out.writeUTF(c.digest); out.writeUTF(target.identity.path); out.writeUTF(target.identity.digest)
                out.writeUTF(d.source); out.writeUTF(d.translated); out.writeUTF(d.script.name); out.writeUTF(d.rotation.name)
                val r = requireNotNull(d.regions)
                out.writeInt(r.width); out.writeInt(r.height); out.writeBoolean(r.partial); out.writeInt(r.regions.size)
                for (region in r.regions) {
                    out.writeInt(region.id); out.writeInt(region.box.left); out.writeInt(region.box.top)
                    out.writeInt(region.box.right); out.writeInt(region.box.bottom); out.writeUTF(region.text)
                    out.writeUTF(d.edits[region.id].orEmpty())
                }
            }
        }
        return hash(bytes.toByteArray())
    }

    @Synchronized
    fun save(directory: File, queue: ImageAssetDiscovery.Queue, request: ImageBatchExchange.Request) {
        require(request.targets.all { it.candidate in queue.candidates })
        val file = location(directory, queue.root)
        require(directory.isDirectory || directory.mkdirs())
        require(!Files.isSymbolicLink(directory.toPath()))
        require(!Files.exists(file.toPath(), NOFOLLOW_LINKS) || Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        val temp = Files.createTempFile(directory.toPath(), "exchange-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { raw ->
                val out = DataOutputStream(raw)
                out.writeUTF(MAGIC); out.writeUTF(request.token); out.writeUTF(fingerprint(request))
                out.flush(); raw.fd.sync()
            }
            Files.move(temp, file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    @Synchronized
    fun restore(directory: File, queue: ImageAssetDiscovery.Queue, drafts: File,
        checkpoint: () -> Unit = {}): ImageBatchExchange.Request? {
        val file = location(directory, queue.root)
        if (!Files.exists(file.toPath(), NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.length() in 1L..LIMIT.toLong())
        val data = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { val bytes = ByteArray(LIMIT + 1)
            var size = 0
            while (size < bytes.size) {
                val n = it.read(bytes, size, bytes.size - size)
                if (n < 0) break
                size += n
            }
            bytes.copyOf(size) }
        require(data.size <= LIMIT)
        val (token, expected) = DataInputStream(data.inputStream()).use {
            require(it.readUTF() == MAGIC)
            val pair = it.readUTF() to it.readUTF()
            require(it.read() == -1)
            pair
        }
        val request = ImageBatchExchange.prepare(queue, drafts, token, checkpoint)
        require(fingerprint(request) == expected) { "원본 또는 번역 초안이 변경되어 이전 요청을 복원할 수 없습니다." }
        return request
    }
}
