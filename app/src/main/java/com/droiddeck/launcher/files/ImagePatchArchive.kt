package com.droiddeck.launcher.files

import java.io.*
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Versioned same-format image patch. Never extracts or modifies a game file. */
internal object ImagePatchArchive {
    const val MAX_FILE = 8 * 1024 * 1024
    const val MAX_TOTAL = 64L * 1024 * 1024
    const val MAX_ARCHIVE = MAX_TOTAL + 1024 * 1024
    private const val MAX_MANIFEST = 256 * 1024
    private const val MANIFEST = "manifest.ddip"
    private const val MAGIC = "DDIMAGEPATCH1"
    data class Source(val relative: String, val originalHash: String, val originalBytes: Long, val width: Int, val height: Int)
    data class Row(val source: Source, val outputHash: String, val outputBytes: Int)
    data class Package(val file: File, val rows: List<Row>, val digest: String)

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun hash(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun validHash(value: String) = value.matches(Regex("[0-9a-f]{64}"))
    private fun validate(source: Source) {
        val path = source.relative
        require(path.length in 1..4096 && path.toByteArray(Charsets.UTF_8).size <= 8192)
        require(path.split('/').all { part -> part.isNotBlank() && part != "." && part != ".." &&
            part.length <= 255 && !part.endsWith('.') && !part.endsWith(' ') &&
            part.none { it.isISOControl() || it in "\\:\"*?<>|" } &&
            !part.substringBefore('.').uppercase(Locale.ROOT).matches(Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) })
        require(path.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("png", "tga", "dds"))
        require(!path.substringAfterLast('/').startsWith("dd-translated-", ignoreCase = true))
        require(validHash(source.originalHash) && source.originalBytes in 1L..MAX_FILE.toLong())
        require(source.width in 1..8192 && source.height in 1..8192 && source.width.toLong() * source.height <= 8_388_608L)
    }
    private fun validateSources(sources: List<Source>) {
        require(sources.size in 1..20)
        sources.forEach(::validate)
        val paths = sources.map { it.relative.lowercase(Locale.ROOT) }
        require(paths.distinct().size == paths.size)
        require(paths.none { a -> paths.any { b -> b != a && b.startsWith("$a/") } })
    }
    private fun manifest(rows: List<Row>): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            out.writeUTF(MAGIC); out.writeInt(rows.size)
            rows.forEach { row ->
                val s = row.source
                out.writeUTF(s.relative); out.writeUTF(s.originalHash); out.writeLong(s.originalBytes)
                out.writeInt(s.width); out.writeInt(s.height); out.writeUTF(row.outputHash); out.writeInt(row.outputBytes)
            }
        }
    }.toByteArray().also { require(it.size <= MAX_MANIFEST) }
    private fun parse(bytes: ByteArray): List<Row> = DataInputStream(bytes.inputStream()).use { input ->
        require(input.readUTF() == MAGIC)
        val count = input.readInt(); require(count in 1..20)
        val rows = List(count) {
            val source = Source(input.readUTF(), input.readUTF(), input.readLong(), input.readInt(), input.readInt())
            Row(source, input.readUTF(), input.readInt()).also {
                require(validHash(it.outputHash) && it.outputBytes in 1..MAX_FILE)
            }
        }
        require(input.read() == -1)
        validateSources(rows.map { it.source })
        require(rows.sumOf { it.outputBytes.toLong() } <= MAX_TOTAL)
        rows
    }
    private fun add(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED; size = bytes.size.toLong(); compressedSize = size
            crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry()
    }
    private fun read(zip: ZipFile, entry: ZipEntry, limit: Int): ByteArray {
        require(!entry.isDirectory && entry.method == ZipEntry.STORED && entry.size in 1L..limit.toLong() && entry.compressedSize == entry.size)
        return zip.getInputStream(entry).use { raw ->
            DataInputStream(raw).use { input ->
                ByteArray(entry.size.toInt()).also { bytes ->
                    input.readFully(bytes); require(input.read() == -1)
                    require(CRC32().apply { update(bytes) }.value == entry.crc)
                }
            }
        }
    }
    fun verify(file: File, checkpoint: () -> Unit = {}): List<Row> {
        checkpoint()
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() in 1L..MAX_ARCHIVE)
        return ZipFile(file).use { zip ->
            require(zip.size() in 2..21)
            val entries = linkedMapOf<String, ZipEntry>()
            val iterator = zip.entries()
            while (iterator.hasMoreElements()) {
                val entry = iterator.nextElement()
                require(entries.put(entry.name, entry) == null && !entry.isDirectory)
            }
            val rows = parse(read(zip, requireNotNull(entries[MANIFEST]), MAX_MANIFEST))
            require(entries.keys == rows.map { "files/${it.source.relative}" }.toSet() + MANIFEST)
            for (row in rows) {
                checkpoint()
                val bytes = read(zip, requireNotNull(entries["files/${row.source.relative}"]), MAX_FILE)
                require(bytes.size == row.outputBytes && hash(bytes) == row.outputHash)
            }
            checkpoint()
            rows
        }
    }
    fun digest(file: File, checkpoint: () -> Unit = {}): String {
        require(Files.isRegularFile(file.toPath(), NOFOLLOW_LINKS) && file.length() in 1L..MAX_ARCHIVE)
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(file.toPath(), NOFOLLOW_LINKS).use { input ->
            val buffer = ByteArray(65536)
            var total = 0L
            while (true) {
                checkpoint()
                val n = input.read(buffer)
                if (n < 0) break
                total += n; require(total <= MAX_ARCHIVE)
                digest.update(buffer, 0, n)
            }
        }
        return hex(digest.digest())
    }
    fun create(root: File, directory: File, sources: List<Source>, load: (Source) -> ByteArray,
        checkpoint: () -> Unit = {}): Package {
        validateSources(sources)
        require(Files.isDirectory(root.toPath(), NOFOLLOW_LINKS))
        val game = root.canonicalFile.toPath(); val dest = directory.canonicalFile.toPath()
        require(!game.startsWith(dest) && !dest.startsWith(game))
        require(!Files.isSymbolicLink(directory.toPath()))
        require(directory.isDirectory || directory.mkdirs())
        val file = Files.createTempFile(directory.toPath(), "image-patch-", ".zip").toFile()
        try {
            var total = 0L
            val rows = mutableListOf<Row>()
            FileOutputStream(file).use { raw ->
                val zip = ZipOutputStream(raw)
                zip.use {
                    for (source in sources) {
                        checkpoint()
                        val bytes = load(source)
                        require(bytes.size in 1..MAX_FILE)
                        total += bytes.size; require(total <= MAX_TOTAL)
                        rows.add(Row(source, hash(bytes), bytes.size))
                        add(zip, "files/${source.relative}", bytes)
                    }
                    add(zip, MANIFEST, manifest(rows))
                    zip.finish(); zip.flush(); raw.fd.sync()
                }
            }
            require(verify(file, checkpoint) == rows)
            for (row in rows) {
                checkpoint()
                val current = load(row.source)
                require(current.size == row.outputBytes && hash(current) == row.outputHash) { "승인본이 변경됐습니다." }
            }
            return Package(file, rows.toList(), digest(file, checkpoint))
        } catch (failure: Throwable) {
            Files.deleteIfExists(file.toPath())
            throw failure
        }
    }
}
