package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Locale

/** Use the existing folder listing only; never recursively search or read candidate contents. */
internal object SavedExchangeFiles {
    private val extensions = setOf("ini", "cfg", "log", "tsv", "csv", "json")
    private const val DISPLAY_LIMIT = 30

    data class Page(val files: List<File>, val more: Boolean)

    private fun directChild(folder: File, file: File): Boolean =
        file.absoluteFile.parentFile == folder.absoluteFile &&
            file.name !in setOf(".", "..") &&
            file.extension.lowercase(Locale.ROOT) in extensions

    fun candidates(folder: File, listed: List<File>, query: String): Page {
        val matches = listed.asSequence().filter { directChild(folder, it) }
            .filter { it.name.contains(query.trim(), ignoreCase = true) }
            .distinctBy { it.absolutePath }.take(DISPLAY_LIMIT + 1).toList()
        return Page(matches.take(DISPLAY_LIMIT), matches.size > DISPLAY_LIMIT)
    }

    /** Recheck the selected leaf before opening; full revision binding is checked in the editor. */
    fun selected(folder: File, file: File): File {
        require(directChild(folder, file)) { "현재 폴더의 지원 파일을 선택하세요." }
        require(Files.isDirectory(folder.toPath(), NOFOLLOW_LINKS) &&
            Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS)) { "파일 또는 폴더가 변경되었습니다." }
        require(file.canonicalFile.parentFile == folder.canonicalFile) { "현재 폴더 밖의 파일은 열 수 없습니다." }
        return file
    }
}
