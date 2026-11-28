package com.droiddeck.launcher.files

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Export-only archive of complete saved drafts. Never applies a patch to the game. */
internal object TranslationWorklistExport {
    private const val MAX_TOTAL = 8 * 1024 * 1024
    private const val MAX_ARCHIVE = MAX_TOTAL + 512 * 1024

    data class Row(val candidate: TranslationDiscovery.Candidate, val translatedHash: String, val bytes: Int)
    class Plan internal constructor(val rows: List<Row>, private val archive: ByteArray) {
        val size get() = archive.size
        fun writeTo(output: OutputStream) { output.write(archive) }
    }

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun paths(queue: TranslationWorklist.Queue) {
        val names = queue.files.map { candidate ->
            val path = candidate.relative
            require(TranslationDiscovery.validRelative(path) && path.toByteArray(Charsets.UTF_8).size <= 8192)
            require(path.split('/').all { part ->
                part.length <= 255 && !part.endsWith('.') && !part.endsWith(' ') &&
                    part.none { it in "\\:\"*?<>|" } &&
                    !part.substringBefore('.').uppercase(Locale.ROOT).matches(Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]"))
            }) { "ZIP으로 내보낼 수 없는 파일 경로입니다." }
            path.lowercase(Locale.ROOT)
        }
        require(names.distinct().size == names.size && names.none { a ->
            names.any { b -> b != a && b.startsWith(a + "/") }
        }) { "파일 경로가 충돌합니다." }
    }

    private fun payload(queue: TranslationWorklist.Queue, candidate: TranslationDiscovery.Candidate,
        drafts: File): ByteArray {
        val snapshot = TranslationDiscovery.snapshot(queue.root, candidate)
        val edits = requireNotNull(TranslationDraftStore.load(drafts, snapshot.file.canonicalPath, snapshot.source)) {
            "저장된 번역 초안이 없습니다: " + candidate.relative
        }
        require(TranslationReuse.review(snapshot.source, edits).count(TranslationReuse.Status.READY) == candidate.rows) {
            "모든 문장을 완성하고 초안을 저장하세요: " + candidate.relative
        }
        val proposed = GamePatchAuthor.translation(snapshot.source, edits).proposed
        return proposed.toByteArray(Charsets.UTF_8).also {
            require(it.size in 1..TranslationLimits.MAX_PATCH_BYTES)
        }
    }

    private fun add(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name).apply { time = 0L })
        zip.write(bytes)
        zip.closeEntry()
    }

    fun prepare(queue: TranslationWorklist.Queue, drafts: File, checkpoint: () -> Unit = {}): Plan {
        val stable = queue.copy(files = queue.files.toList())
        paths(stable)
        val output = ByteArrayOutputStream()
        val rows = mutableListOf<Row>()
        var total = 0
        ZipOutputStream(output).use { zip ->
            for (candidate in stable.files) {
                checkpoint()
                val bytes = payload(stable, candidate, drafts)
                total += bytes.size
                require(total <= MAX_TOTAL) { "번역 파일 합계가 8 MiB를 초과합니다." }
                rows += Row(candidate, hash(bytes), bytes.size)
                add(zip, "files/" + candidate.relative, bytes)
                require(output.size() <= MAX_ARCHIVE)
            }
            val manifest = buildString {
                append("DDTEXTEXPORT1\nrelative_path\tsource_sha256\ttranslated_sha256\tbytes\n")
                for (row in rows) {
                    append(row.candidate.relative).append('\t').append(row.candidate.digest)
                        .append('\t').append(row.translatedHash).append('\t').append(row.bytes).append('\n')
                }
            }
            add(zip, "manifest.tsv", manifest.toByteArray(Charsets.UTF_8))
        }
        require(output.size() <= MAX_ARCHIVE)
        checkpoint()
        return Plan(rows.toList(), output.toByteArray())
    }

    /** Refuse publication of a reviewed snapshot if the current source or saved draft changed. */
    fun recheck(plan: Plan, queue: TranslationWorklist.Queue, drafts: File, checkpoint: () -> Unit = {}) {
        val stable = queue.copy(files = queue.files.toList())
        paths(stable)
        require(stable.files == plan.rows.map { it.candidate })
        for (row in plan.rows) {
            checkpoint()
            val current = payload(stable, row.candidate, drafts)
            require(current.size == row.bytes && hash(current) == row.translatedHash) {
                "검토 이후 번역 초안이 변경됐습니다: " + row.candidate.relative
            }
        }
        checkpoint()
    }
}
