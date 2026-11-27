package com.droiddeck.launcher.files

import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.BasicFileAttributes

/** Read-only discovery, never a permission to apply a patch. */
internal object TranslationDiscovery {
    private val formats = setOf("csv", "tsv", "json")
    data class Candidate(val relative: String, val rows: Int)
    data class Report(val candidates: List<Candidate>, val examined: Int, val rejected: Int, val partial: Boolean)

    fun inspect(root: File, visitLimit: Int = 5000, probeLimit: Int = 32,
        resultLimit: Int = 20, checkpoint: () -> Unit = {}): Report {
        require(visitLimit in 1..5000 && probeLimit in 1..32 && resultLimit in 1..20)
        val start = root.toPath()
        require(Files.isDirectory(start, NOFOLLOW_LINKS))
        var visited = 0
        var examined = 0
        var rejected = 0
        var partial = false
        val candidates = mutableListOf<Candidate>()
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
                if (!attrs.isRegularFile || path.toFile().extension.lowercase() !in formats) return step
                if (examined >= probeLimit || candidates.size >= resultLimit) {
                    partial = true
                    return FileVisitResult.TERMINATE
                }
                examined++
                val relative = start.relativize(path).toString()
                val count = try {
                    if (attrs.size() !in 1L..TranslationLimits.MAX_PATCH_BYTES.toLong()) null
                    else validate(root, relative)
                } catch (_: IOException) { null }
                  catch (_: IllegalArgumentException) { null }
                  catch (_: SecurityException) { null }
                checkpoint()
                if (count == null) rejected++ else candidates.add(Candidate(relative, count))
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
        return Report(candidates.sortedBy { it.relative }, examined, rejected, partial)
    }

    private fun resolve(root: File, relative: String): File {
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val parts = relative.split('/')
        require(parts.isNotEmpty() && parts.none { it.isBlank() || it == "." || it == ".." || '\\' in it })
        var file = root.canonicalFile
        for (part in parts) {
            file = File(file, part)
            require(!Files.isSymbolicLink(file.toPath()))
        }
        require(file.extension.lowercase() in formats && Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS))
        return file
    }

    private fun validate(root: File, relative: String): Int {
        val file = resolve(root, relative)
        val source = LocalGameAdvisor.decode(GamePatchStore.readTarget(file))
        require(GamePatchAuthor.matchesTranslationExtension(source, file.extension))
        return GamePatchAuthor.extract(source).size
    }

    /** Recheck current path and format before navigating to the existing editor. */
    fun open(root: File, candidate: Candidate): File {
        require(validate(root, candidate.relative) == candidate.rows) { "파일이 변경되었습니다. 다시 탐색하세요." }
        return resolve(root, candidate.relative)
    }
}
