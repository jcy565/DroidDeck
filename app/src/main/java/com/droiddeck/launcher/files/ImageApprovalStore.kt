package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest

/** Explicitly approved same-format assets, kept separately from the game and temporary previews. */
internal object ImageApprovalStore {
    private const val MAGIC = "DDIMAGEAPPROVAL1"
    private const val RECORD_LIMIT = ImageBatchRender.MAX_FILE_BYTES + 32768
    data class Approved(val candidate: ImageAssetDiscovery.Candidate, val bytes: ByteArray)
    enum class State { MISSING, VALID, INVALID }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun draftHash(draft: ImageDraftStore.Draft): String {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeUTF(draft.source); out.writeUTF(draft.translated)
            out.writeUTF(draft.script.name); out.writeUTF(draft.rotation.name)
            val r = requireNotNull(draft.regions)
            out.writeInt(r.width); out.writeInt(r.height); out.writeBoolean(r.partial)
            out.writeUTF(r.text); out.writeInt(r.regions.size)
            for (region in r.regions) {
                out.writeInt(region.id); out.writeInt(region.box.left); out.writeInt(region.box.top)
                out.writeInt(region.box.right); out.writeInt(region.box.bottom)
                out.writeUTF(region.text); out.writeUTF(draft.edits[region.id].orEmpty())
            }
        }
        return hash(body.toByteArray())
    }
    private fun folder(directory: File, root: File): File {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val privatePath = directory.canonicalFile.toPath()
        val gamePath = root.canonicalFile.toPath()
        require(!privatePath.startsWith(gamePath) && !gamePath.startsWith(privatePath))
        require(!Files.isSymbolicLink(directory.toPath()))
        return File(directory, hash(root.canonicalPath.toByteArray(Charsets.UTF_8))).also {
            require(!Files.isSymbolicLink(it.toPath()))
        }
    }
    private fun record(folder: File, c: ImageAssetDiscovery.Candidate): File =
        File(folder, hash(c.relative.toByteArray(Charsets.UTF_8)) + ".approved")

    private fun current(queue: ImageAssetDiscovery.Queue, drafts: File,
        c: ImageAssetDiscovery.Candidate): Pair<ImageDraftStore.Identity, ImageDraftStore.Draft> {
        require(c in queue.candidates)
        val file = ImageAssetDiscovery.open(queue.root, c)
        val id = ImageDraftStore.Identity(file.canonicalPath, c.digest)
        val draft = requireNotNull(ImageDraftStore.load(drafts, id))
        ImageBatchRenderPlan.requireReady(c, draft)
        return id to draft
    }

    @Synchronized
    fun approve(directory: File, queue: ImageAssetDiscovery.Queue, drafts: File,
        artifact: ImageBatchRender.Artifact, verify: (ByteArray, String, ImageAssetDiscovery.Candidate) -> Unit,
        checkpoint: () -> Unit = {}) {
        checkpoint()
        val c = artifact.candidate
        val ext = File(c.relative).extension.lowercase()
        require(ext in setOf("png", "tga", "dds") && artifact.file.extension == ext) {
            "원본과 같은 형식의 PNG·TGA·DDS만 패치 후보로 승인할 수 있습니다."
        }
        val (id, draft) = current(queue, drafts, c)
        require(id == artifact.identity && draft == artifact.draft) { "원본 또는 초안이 변경됐습니다." }
        val bytes = ImageBatchRender.readVerified(artifact)
        verify(bytes, ext, c)
        val folder = folder(directory, queue.root)
        require(folder.isDirectory || folder.mkdirs())
        require(Files.isDirectory(folder.toPath(), NOFOLLOW_LINKS))
        val destination = record(folder, c)
        var count = 0
        var total = 0L
        Files.newDirectoryStream(folder.toPath()).use { files ->
            for (path in files) {
                checkpoint()
                require(Files.isRegularFile(path, NOFOLLOW_LINKS) && path.fileName.toString().matches(Regex("[0-9a-f]{64}\\.approved")))
                if (path == destination.toPath()) continue
                count++
                require(count < 20)
                total += Files.size(path)
                require(total <= ImageBatchRender.MAX_BATCH_BYTES)
            }
        }
        val header = ByteArrayOutputStream().also { body ->
            DataOutputStream(body).use { out ->
                out.writeUTF(MAGIC); out.writeUTF(c.relative); out.writeUTF(id.path); out.writeUTF(id.digest)
                out.writeUTF(draftHash(draft)); out.writeUTF(hash(bytes)); out.writeInt(bytes.size)
                out.writeInt(c.width); out.writeInt(c.height)
            }
        }.toByteArray()
        require(header.size + bytes.size <= RECORD_LIMIT && total + header.size + bytes.size <= ImageBatchRender.MAX_BATCH_BYTES)
        val temp = Files.createTempFile(folder.toPath(), ".approve-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { out -> out.write(header); out.write(bytes); out.fd.sync() }
            checkpoint()
            require(current(queue, drafts, c) == (id to draft))
            Files.move(temp, destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }

    @Synchronized
    fun read(directory: File, queue: ImageAssetDiscovery.Queue, drafts: File,
        c: ImageAssetDiscovery.Candidate, verify: (ByteArray, String, ImageAssetDiscovery.Candidate) -> Unit): Approved? {
        require(c in queue.candidates)
        val file = record(folder(directory, queue.root), c)
        if (!Files.exists(file.toPath(), NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() in 1L..RECORD_LIMIT.toLong())
        val (id, draft) = current(queue, drafts, c)
        val ext = File(c.relative).extension.lowercase()
        require(ext in setOf("png", "tga", "dds"))
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { raw ->
            DataInputStream(BufferedInputStream(raw)).use { input ->
                require(input.readUTF() == MAGIC && input.readUTF() == c.relative)
                require(input.readUTF() == id.path && input.readUTF() == id.digest && input.readUTF() == draftHash(draft))
                val digest = input.readUTF()
                val length = input.readInt()
                require(length in 1..ImageBatchRender.MAX_FILE_BYTES)
                require(input.readInt() == c.width && input.readInt() == c.height)
                ByteArray(length).also { input.readFully(it); require(input.read() == -1 && hash(it) == digest) }
            }
        }
        verify(bytes, ext, c)
        require(current(queue, drafts, c) == (id to draft))
        return Approved(c, bytes)
    }

    @Synchronized
    fun revoke(directory: File, queue: ImageAssetDiscovery.Queue, c: ImageAssetDiscovery.Candidate) {
        require(c in queue.candidates)
        val file = record(folder(directory, queue.root), c)
        if (Files.exists(file.toPath(), NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
            Files.delete(file.toPath())
        }
    }
}
