package com.droiddeck.launcher.files

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Offline, explicit edits only. Does not infer game-specific semantics or translate text itself. */
internal object GamePatchAuthor {
    private val assignment = Regex("""모드\s*:\s*([A-Za-z0-9_. -]{1,80})\s*=\s*(true|false|[-+]?\d{1,9}(?:\.\d{1,6})?)""", RegexOption.IGNORE_CASE)
    private val number = Regex("""[-+]?\d{1,9}(?:\.\d{1,6})?""")
    private val keyPattern = Regex("[A-Za-z0-9_.-]{1,80}")
    private val token = Regex("""\{[A-Za-z0-9_]{1,40}\}|%(?:[1-9][0-9]?\$)?[sdif]|%%|\\[nrt\\]""")
    private const val LIMIT = 1024 * 1024
    private const val MAX_ROWS = 100

    fun mod(original: String, section: String?, command: String): GameConfigPreview.Preview {
        require(command.length <= 160)
        val match = requireNotNull(assignment.matchEntire(command.trim())) {
            "모드: 기존키=숫자 또는 true/false 형식을 사용하세요."
        }
        val key = match.groupValues[1].trim()
        val value = match.groupValues[2]
        require(key.isNotBlank())
        val preview = GameConfigPreview.preview(original, section, mapOf(key to value))
        val previous = preview.changes.singleOrNull()?.before ?: value
        val bool = { s: String -> s.equals("true", true) || s.equals("false", true) }
        require((number.matches(previous) && number.matches(value)) ||
            (bool(previous) && bool(value))) { "기존 값과 같은 숫자 또는 논리값만 수정할 수 있습니다." }
        return preview
    }

    data class Row(val id: String, val text: String)

    private fun tokens(text: String): List<String> {
        require(text.isNotBlank() && text.length <= 1000 && text.none(Char::isISOControl))
        val parts = token.findAll(text).map { it.value }.toList()
        require(token.replace(text, "").none { it in "{}%\\<>" }) {
            "지원하지 않는 변수, 이스케이프 또는 태그가 있습니다."
        }
        return parts
    }

    fun matchesTranslationExtension(source: String, extension: String): Boolean =
        if (TranslationJson.isCandidate(source)) extension.equals("json", ignoreCase = true)
        else TranslationTable.matchesExtension(source, extension)

    fun extract(source: String): List<Row> {
        require(source.length <= 262144)
        if (TranslationJson.isCandidate(source)) {
            return TranslationJson.entries(source).map { entry ->
                require(keyPattern.matches(entry.id)) { "지원하지 않는 JSON 키입니다." }
                tokens(entry.text)
                Row(entry.id, entry.text)
            }
        }
        val format = TranslationTable.format(source)
        val lines = source.lineSequence().filter { it.isNotEmpty() }.iterator()
        lines.next()
        val rows = mutableListOf<Row>()
        val ids = mutableSetOf<String>()
        for (line in lines) {
            val parsed = TranslationTable.row(line, format)
            require(keyPattern.matches(parsed.id) && ids.add(parsed.id)) {
                "중복 ID 또는 지원하지 않는 번역 행입니다."
            }
            tokens(parsed.text)
            require(rows.size < MAX_ROWS) { "최대 100개 문장을 지원합니다." }
            rows.add(Row(parsed.id, parsed.text))
        }
        require(rows.isNotEmpty())
        return rows
    }

    fun translation(source: String, edits: Map<String, String>): GameConfigPreview.Preview {
        val rows = extract(source).associateBy { it.id }
        require(edits.isNotEmpty() && edits.keys.all { it in rows })
        for ((id, translated) in edits) {
            require(tokens(rows.getValue(id).text) == tokens(translated)) {
                "변수와 이스케이프의 종류·개수·순서를 유지하세요: $id"
            }
        }
        if (TranslationJson.isCandidate(source)) {
            val proposed = TranslationJson.rewrite(source, edits)
            require(proposed.toByteArray(Charsets.UTF_8).size <= LIMIT)
            return GameConfigPreview.Preview(source, proposed, emptyList())
        }
        val format = TranslationTable.format(source)
        var header = true
        val proposed = Regex("[^\\r\\n]+|\\r\\n|\\r|\\n").replace(source) { match ->
            val line = match.value
            if (line == "\r" || line == "\n" || line == "\r\n") line
            else if (header) { header = false; line }
            else {
                val row = TranslationTable.row(line, format)
                edits[row.id]?.let { if (it == row.text) line else TranslationTable.replace(row, it, format) } ?: line
            }
        }
        require(proposed.toByteArray(Charsets.UTF_8).size <= LIMIT)
        return GameConfigPreview.Preview(source, proposed, emptyList())
    }

    fun zip(targetName: String, preview: GameConfigPreview.Preview): ByteArray {
        require(targetName.length in 1..180 && targetName != "." && targetName != ".." &&
            targetName.none { it.isISOControl() || it == '/' || it == '\\' || it == ':' })
        require(targetName.substringAfterLast('.').lowercase() in GamePatchStore.extensions)
        require(preview.original != preview.proposed)
        val payload = preview.proposed.toByteArray(Charsets.UTF_8)
        require(payload.size <= LIMIT)
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(targetName))
            zip.write(payload)
            zip.closeEntry()
        }
        return out.toByteArray().also { require(it.size <= LIMIT) }
    }
}
