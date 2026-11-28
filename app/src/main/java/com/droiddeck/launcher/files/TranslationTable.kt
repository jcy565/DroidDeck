package com.droiddeck.launcher.files

/** Strict two-column translation tables. CSV multiline fields are deliberately unsupported. */
internal object TranslationTable {
    enum class Format { TSV, CSV }
    data class CellRow(val id: String, val text: String, val prefix: String)

    fun format(source: String): Format {
        val header = source.lineSequence().firstOrNull { it.isNotEmpty() }
        return when (header) {
            "id\ttext" -> Format.TSV
            "id,text", "\"id\",\"text\"", "id,\"text\"", "\"id\",text" -> Format.CSV
            else -> error("id/text 두 열의 UTF-8 TSV 또는 CSV만 지원합니다.")
        }
    }

    fun matchesExtension(source: String, extension: String): Boolean =
        format(source).name.equals(extension, ignoreCase = true)

    fun row(line: String, format: Format): CellRow = when (format) {
        Format.TSV -> {
            val parts = line.split('\t')
            require(parts.size == 2) { "TSV 행은 두 열이어야 합니다." }
            CellRow(parts[0], parts[1], parts[0] + "\t")
        }
        Format.CSV -> {
            val first = csvField(line, 0)
            require(first.second < line.length && line[first.second] == ',') { "CSV 행은 두 열이어야 합니다." }
            val second = csvField(line, first.second + 1)
            require(second.second == line.length) { "CSV 행은 두 열이어야 합니다." }
            CellRow(first.first, second.first, line.substring(0, first.second + 1))
        }
    }

    private fun csvField(line: String, start: Int): Pair<String, Int> {
        if (start == line.length) return "" to start
        if (line[start] != '"') {
            val end = line.indexOf(',', start).let { if (it < 0) line.length else it }
            val text = line.substring(start, end)
            require('"' !in text) { "CSV 따옴표 형식이 올바르지 않습니다." }
            return text to end
        }
        val text = StringBuilder()
        var pos = start + 1
        while (pos < line.length) {
            val char = line[pos++]
            if (char != '"') text.append(char)
            else if (pos < line.length && line[pos] == '"') {
                text.append('"')
                pos++
            } else {
                require(pos == line.length || line[pos] == ',') { "CSV 따옴표 뒤에 잘못된 문자가 있습니다." }
                return text.toString() to pos
            }
        }
        error("닫히지 않은 CSV 따옴표 또는 여러 줄 필드는 지원하지 않습니다.")
    }

    fun replace(row: CellRow, translated: String, format: Format): String = when (format) {
        Format.TSV -> row.prefix + translated
        Format.CSV -> row.prefix + "\"" + translated.replace("\"", "\"\"") + "\""
    }
}
