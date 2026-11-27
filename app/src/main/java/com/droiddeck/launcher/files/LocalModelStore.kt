package com.droiddeck.launcher.files

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.security.MessageDigest
import java.util.Locale

/** Only user-selected, checksum-pinned data; no downloads or executable imports. */
internal object LocalModelStore {
    const val MAX_BYTES = 1024L * 1024 * 1024
    private const val MAX_MODELS = 2
    private val digestPattern = Regex("[a-fA-F0-9]{64}")

    fun validDigest(value: String): Boolean = digestPattern.matches(value)

    private fun target(directory: File, digest: String): File {
        require(validDigest(digest)) { "공식 모델 SHA-256을 입력하세요." }
        require(!Files.isSymbolicLink(directory.toPath()))
        return File(directory, digest.lowercase(Locale.ROOT) + ".task")
    }

    private fun hash(input: InputStream, allowed: () -> Boolean,
        write: (ByteArray, Int) -> Unit = { _, _ -> }): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var total = 0L
        while (true) {
            check(allowed()) { "게임 실행 또는 메모리 상태 변경으로 중단했습니다." }
            val count = input.read(buffer)
            if (count < 0) break
            require(count > 0)
            total += count
            require(total <= MAX_BYTES) { "모델은 최대 1GiB까지 지원합니다." }
            md.update(buffer, 0, count)
            write(buffer, count)
        }
        require(total > 0)
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Synchronized
    fun verified(directory: File, digest: String, allowed: () -> Boolean): File {
        val file = target(directory, digest)
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() in 1..MAX_BYTES) {
            "일치하는 모델을 먼저 가져오세요."
        }
        val actual = Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { hash(it, allowed) }
        require(actual.equals(digest, true)) { "모델 SHA-256이 일치하지 않습니다." }
        return file
    }

    @Synchronized
    fun install(directory: File, digest: String, input: InputStream, allowed: () -> Boolean): File {
        val file = target(directory, digest)
        check(allowed())
        require(directory.isDirectory || directory.mkdirs())
        require(!Files.isSymbolicLink(directory.toPath()))
        if (Files.exists(file.toPath(), NOFOLLOW_LINKS)) return verified(directory, digest, allowed)
        val entries = requireNotNull(directory.listFiles())
        require(entries.size < MAX_MODELS) { "모델 저장 한도에 도달했습니다." }
        val temp = Files.createTempFile(directory.toPath(), ".model-", ".tmp")
        try {
            val actual = temp.toFile().outputStream().use { output ->
                val result = hash(input, allowed) { bytes, count -> output.write(bytes, 0, count) }
                output.fd.sync()
                result
            }
            require(actual.equals(digest, true)) { "모델 SHA-256이 일치하지 않습니다." }
            check(allowed())
            Files.move(temp, file.toPath(), ATOMIC_MOVE)
            return file
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}
