package com.droiddeck.launcher.files

import java.io.File
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes

internal object FolderUsage {
    data class Report(val files: Int, val bytes: Long, val temporaryBytes: Long,
                      val candidates: List<String>, val partial: Boolean)

    fun inspect(root: File, limit: Int = 10000, checkpoint: () -> Unit = {}): Report {
        val start = root.toPath()
        require(Files.isDirectory(start, LinkOption.NOFOLLOW_LINKS))
        var visited = 0
        var files = 0
        var bytes = 0L
        var temporary = 0L
        var partial = false
        val names = mutableMapOf<Pair<String, Long>, String>()
        val candidates = mutableListOf<String>()
        fun next(): FileVisitResult {
            checkpoint()
            if (++visited <= limit) return FileVisitResult.CONTINUE
            partial = true
            return FileVisitResult.TERMINATE
        }
        Files.walkFileTree(start, emptySet(), 32, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = next()
            override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                val step = next()
                if (attrs.isDirectory) partial = true
                if (step == FileVisitResult.TERMINATE || !attrs.isRegularFile) return step
                val relative = start.relativize(path)
                val name = path.fileName.toString()
                files++
                bytes += attrs.size()
                if (name.endsWith(".tmp", true) || relative.any { it.toString().equals("cache", true) }) temporary += attrs.size()
                if (attrs.size() > 0 && candidates.size < 5) {
                    val previous = names.putIfAbsent(name to attrs.size(), relative.toString())
                    if (previous != null) candidates.add("$previous ↔ $relative")
                }
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
        return Report(files, bytes, temporary, candidates, partial)
    }
}
