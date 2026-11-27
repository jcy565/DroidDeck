package com.droiddeck.launcher.files

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/** Bounded staging only. Never writes beside a game asset or marks it approved. */
internal object ImageBatchRender {
    const val MAX_FILE_BYTES = 8 * 1024 * 1024
    const val MAX_BATCH_BYTES = 64L * 1024 * 1024
    data class Artifact(val candidate: ImageAssetDiscovery.Candidate, val file: File,
        val identity: ImageDraftStore.Identity, val draft: ImageDraftStore.Draft, val bytes: Long, val digest: String)
    enum class State { STAGED, SKIPPED, FAILED, LIMIT }
    data class Entry(val relative: String, val state: State, val artifact: Artifact? = null)
    data class Report(val entries: List<Entry>)

    fun extension(source: String): String = when (source.lowercase()) {
        "tga" -> "tga"
        "dds" -> "dds"
        "png", "jpg", "jpeg", "webp" -> "png"
        else -> error("지원하지 않는 이미지 형식")
    }

    private class Bounded(private val out: OutputStream, private val limit: Long) : OutputStream() {
        var size = 0L
            private set
        override fun write(b: Int) {
            require(size < limit) { "출력 크기 제한 초과" }
            out.write(b)
            size++
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            require(off >= 0 && len >= 0 && off <= b.size - len)
            require(len.toLong() <= limit - size) { "출력 크기 제한 초과" }
            out.write(b, off, len)
            size += len
        }
    }

    private fun checkedBytes(file: File): ByteArray {
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.length() in 1L..MAX_FILE_BYTES.toLong())
        return Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use(ImageTranslationInput::read)
    }
    private fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun readVerified(artifact: Artifact): ByteArray = checkedBytes(artifact.file).also {
        require(it.size.toLong() == artifact.bytes && hash(it) == artifact.digest) { "검토 파일이 변경됐습니다." }
    }

    fun run(queue: ImageAssetDiscovery.Queue, drafts: File, staging: File,
        encode: (File, ImageDraftStore.Identity, ImageDraftStore.Draft, String, OutputStream, () -> Unit) -> Unit,
        verify: (ByteArray, String, ImageAssetDiscovery.Candidate) -> Unit,
        checkpoint: () -> Unit = {}): Report {
        checkpoint()
        require(staging.isDirectory || staging.mkdirs())
        require(Files.isDirectory(staging.toPath(), NOFOLLOW_LINKS))
        val privatePath = staging.canonicalFile.toPath()
        val gamePath = queue.root.canonicalFile.toPath()
        require(!privatePath.startsWith(gamePath) && !gamePath.startsWith(privatePath))
        val entries = mutableListOf<Entry>()
        var remaining = MAX_BATCH_BYTES
        try {
            for (candidate in queue.candidates) {
                checkpoint()
                if (remaining == 0L) {
                    entries.add(Entry(candidate.relative, State.LIMIT))
                    continue
                }
                var output: File? = null
                var keep = false
                try {
                    val file = ImageAssetDiscovery.open(queue.root, candidate)
                    val id = ImageDraftStore.Identity(file.canonicalPath, candidate.digest)
                    val draft = ImageDraftStore.load(drafts, id)
                    if (draft == null || runCatching { ImageBatchRenderPlan.requireReady(candidate, draft) }.isFailure) {
                        entries.add(Entry(candidate.relative, State.SKIPPED))
                        continue
                    }
                    val ext = extension(file.extension)
                    val created = Files.createTempFile(staging.toPath(), "image-review-", ".$ext").toFile()
                    output = created
                    FileOutputStream(created).use { stream ->
                        val bounded = Bounded(stream, minOf(remaining, MAX_FILE_BYTES.toLong()))
                        encode(file, id, draft, ext, bounded, checkpoint)
                        stream.fd.sync()
                    }
                    checkpoint()
                    val bytes = checkedBytes(created)
                    verify(bytes, ext, candidate)
                    require(ImageAssetDiscovery.open(queue.root, candidate).canonicalPath == id.path)
                    require(ImageDraftStore.load(drafts, id) == draft) { "번역 초안이 변경됐습니다." }
                    checkpoint()
                    val artifact = Artifact(candidate, created, id, draft, bytes.size.toLong(), hash(bytes))
                    remaining -= artifact.bytes
                    entries.add(Entry(candidate.relative, State.STAGED, artifact))
                    keep = true
                } catch (e: CancellationException) { throw e }
                  catch (_: Exception) { entries.add(Entry(candidate.relative, State.FAILED)) }
                finally { if (!keep) output?.let { Files.deleteIfExists(it.toPath()) } }
            }
            checkpoint()
            return Report(entries.toList())
        } catch (failure: Throwable) {
            entries.forEach { it.artifact?.file?.let { file -> Files.deleteIfExists(file.toPath()) } }
            throw failure
        }
    }

    fun discard(report: Report) {
        report.entries.forEach { it.artifact?.file?.let { file -> Files.deleteIfExists(file.toPath()) } }
    }
}
