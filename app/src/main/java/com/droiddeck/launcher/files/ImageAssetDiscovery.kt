package com.droiddeck.launcher.files

import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/** Read-only, bounded discovery of possible image localization targets, not OCR. */
internal object ImageAssetDiscovery {
    data class Size(val width: Int, val height: Int)
    data class Candidate(val relative: String, val width: Int, val height: Int, val bytes: Long, val digest: String)
    data class Report(val candidates: List<Candidate>, val examined: Int, val rejected: Int, val partial: Boolean)
    data class Queue(val root: File, val candidates: List<Candidate>) {
        init { require(candidates.size in 1..20 && candidates.map { it.relative }.distinct().size == candidates.size) }
    }

    private fun resolve(root: File, relative: String): File {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val parts = relative.split('/')
        require(parts.all { it.isNotBlank() && it != "." && it != ".." && '\\' !in it })
        var file = root.canonicalFile
        for (part in parts) {
            file = File(file, part)
            require(!Files.isSymbolicLink(file.toPath()))
        }
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        require(file.extension.lowercase() in ImageTranslationInput.extensions)
        require(!file.name.startsWith("dd-translated-", ignoreCase = true))
        return file
    }

    private fun candidate(root: File, relative: String, probe: (ByteArray, String) -> Size): Candidate {
        val file = resolve(root, relative)
        require(file.length() in 1L..ImageTranslationInput.MAX_BYTES.toLong())
        val bytes = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use(ImageTranslationInput::read)
        val size = probe(bytes, file.extension.lowercase())
        ImageTranslationInput.sampleSize(size.width, size.height)
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return Candidate(relative, size.width, size.height, bytes.size.toLong(), digest)
    }

    fun inspect(root: File, visitLimit: Int = 5000, probeLimit: Int = 32, resultLimit: Int = 20,
        probe: (ByteArray, String) -> Size, checkpoint: () -> Unit = {}): Report {
        require(visitLimit in 1..5000 && probeLimit in 1..32 && resultLimit in 1..20)
        val start = root.toPath()
        require(Files.isDirectory(start, NOFOLLOW_LINKS))
        var visited = 0
        var examined = 0
        var rejected = 0
        var partial = false
        var bytesBudget = 64L * 1024 * 1024
        val results = mutableListOf<Candidate>()
        fun next(): FileVisitResult {
            checkpoint()
            if (++visited <= visitLimit) return FileVisitResult.CONTINUE
            partial = true
            return FileVisitResult.TERMINATE
        }
        Files.walkFileTree(start, emptySet(), 32, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = next()
            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                val step = next()
                if (step != FileVisitResult.CONTINUE) return step
                if (attrs.isDirectory) partial = true
                val file = path.toFile()
                if (!attrs.isRegularFile || file.extension.lowercase() !in ImageTranslationInput.extensions ||
                    file.name.startsWith("dd-translated-", ignoreCase = true)) return step
                // Reserve a full per-file cap: concurrent growth cannot exceed the aggregate budget.
                if (examined >= probeLimit || results.size >= resultLimit ||
                    bytesBudget < ImageTranslationInput.MAX_BYTES) {
                    partial = true
                    return FileVisitResult.TERMINATE
                }
                examined++
                val relative = start.relativize(path).toString()
                val result = try {
                    if (attrs.size() !in 1L..ImageTranslationInput.MAX_BYTES.toLong()) null
                    else {
                        bytesBudget -= ImageTranslationInput.MAX_BYTES
                        candidate(root, relative, probe)
                    }
                } catch (_: IOException) { null }
                  catch (_: IllegalArgumentException) { null }
                  catch (e: IllegalStateException) {
                      if (e is java.util.concurrent.CancellationException) throw e
                      null
                  }
                  catch (_: SecurityException) { null }
                checkpoint()
                if (result == null) rejected++ else results.add(result)
                return step
            }
            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                partial = true
                return next()
            }
            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                if (exc != null) partial = true
                return FileVisitResult.CONTINUE
            }
        })
        return Report(results.sortedBy { it.relative }, examined, rejected, partial)
    }

    fun open(root: File, selected: Candidate): File {
        val file = resolve(root, selected.relative)
        val identity = ImageDraftStore.identity(file)
        require(identity.digest == selected.digest && file.length() == selected.bytes) {
            "이미지 원본이 변경됐습니다. 다시 탐색하세요."
        }
        return file
    }
}
