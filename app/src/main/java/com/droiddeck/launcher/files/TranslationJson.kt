package com.droiddeck.launcher.files

/** Strict flat JSON string dictionaries. Only edited value spans are replaced. */
internal object TranslationJson {
    data class Entry(val id: String, val text: String, val start: Int, val end: Int)

    fun isCandidate(source: String): Boolean =
        source.firstOrNull { it !in " \t\r\n" } == '{'

    private fun validUnicode(text: String) {
        var i = 0
        while (i < text.length) {
            val c = text[i++]
            if (Character.isHighSurrogate(c)) {
                require(i < text.length && Character.isLowSurrogate(text[i++])) { "Invalid Unicode pair" }
            } else require(!Character.isLowSurrogate(c)) { "Invalid Unicode pair" }
        }
    }

    private class Reader(private val source: String) {
        private var pos = 0

        fun whitespace() {
            while (pos < source.length && source[pos] in " \t\r\n") pos++
        }

        fun take(c: Char): Boolean {
            whitespace()
            if (pos >= source.length || source[pos] != c) return false
            pos++
            return true
        }

        fun expect(c: Char) { require(take(c)) { "지원하지 않는 JSON 구조입니다." } }

        fun string(): Triple<String, Int, Int> {
            whitespace()
            val start = pos
            require(pos < source.length && source[pos++] == '"') { "JSON 문자열만 지원합니다." }
            val value = StringBuilder()
            while (pos < source.length) {
                val c = source[pos++]
                when {
                    c == '"' -> {
                        val text = value.toString()
                        validUnicode(text)
                        return Triple(text, start, pos)
                    }
                    c == '\\' -> {
                        require(pos < source.length)
                        when (val escape = source[pos++]) {
                            '"', '\\', '/' -> value.append(escape)
                            'b' -> value.append('\b')
                            'f' -> value.append('\u000c')
                            'n' -> value.append('\n')
                            'r' -> value.append('\r')
                            't' -> value.append('\t')
                            'u' -> {
                                require(pos + 4 <= source.length)
                                val hex = source.substring(pos, pos + 4)
                                require(hex.all { it in "0123456789abcdefABCDEF" })
                                value.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> error("Invalid JSON escape")
                        }
                    }
                    c.code < 32 -> error("Invalid JSON control character")
                    else -> value.append(c)
                }
            }
            error("Unterminated JSON string")
        }

        fun done(): Boolean {
            whitespace()
            return pos == source.length
        }
    }

    fun entries(source: String, maxEntries: Int = TranslationLimits.MAX_ROWS,
        maxChars: Int = TranslationLimits.MAX_SOURCE_CHARS): List<Entry> {
        require(maxEntries in 1..TranslationLimits.MAX_BATCH_ENTRIES)
        require(maxChars in 1..TranslationLimits.MAX_BATCH_BYTES && source.length <= maxChars)
        val reader = Reader(source)
        reader.expect('{')
        val rows = mutableListOf<Entry>()
        val ids = mutableSetOf<String>()
        if (!reader.take('}')) {
            do {
                val (id, _, _) = reader.string()
                reader.expect(':')
                val (text, start, end) = reader.string()
                require(ids.add(id)) { "중복 JSON 키입니다." }
                require(rows.size < maxEntries) { "번역 항목 한도를 초과했습니다." }
                rows.add(Entry(id, text, start, end))
            } while (reader.take(','))
            reader.expect('}')
        }
        require(reader.done() && rows.isNotEmpty()) { "빈 사전 또는 추가 JSON 데이터는 지원하지 않습니다." }
        return rows
    }

    fun quote(text: String): String {
        validUnicode(text)
        return buildString {
            append('"')
            for (c in text) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    else -> {
                        require(c.code >= 32)
                        append(c)
                    }
                }
            }
            append('"')
        }
    }

    fun rewrite(source: String, edits: Map<String, String>): String {
        val entries = entries(source)
        val ids = entries.map { it.id }.toSet()
        require(edits.keys.all { it in ids })
        return buildString {
            var cursor = 0
            for (entry in entries) {
                val translated = edits[entry.id] ?: continue
                if (translated == entry.text) continue
                append(source, cursor, entry.start)
                append(quote(translated))
                cursor = entry.end
            }
            append(source, cursor, source.length)
        }
    }
}
