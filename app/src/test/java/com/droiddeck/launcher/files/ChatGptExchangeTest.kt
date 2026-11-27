package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class ChatGptExchangeTest {
    private val source = "[Game]\nSpeed=1\n"
    private val request = GameAgent.Request("속도 변경", "ini", source)
    private val target = "/game/settings.ini"
    private fun reply(p: ChatGptExchange.Pending) =
        """{"exchange_id":"${p.id}","action":"mod","section":"Game","command":"모드: Speed=2","explanation":"설정 제안"}"""

    @Test fun acceptsMatchingProposalWithoutExposingTargetPath() {
        val p = ChatGptExchange.create(target, request)
        assertTrue(p.prompt.contains(p.id))
        assertFalse(p.prompt.contains(target))
        val result = ChatGptExchange.accept(p, target, source, emptyMap(), reply(p)) as GameAgent.Result.Patch
        assertEquals("[Game]\nSpeed=2\n", result.preview.proposed)
        assertEquals(source, result.preview.original)
    }

    @Test fun rejectsOtherExchangeSourceAndTarget() {
        val p = ChatGptExchange.create(target, request)
        val other = ChatGptExchange.create(target, request)
        assertNotEquals(p.id, other.id)
        assertTrue(runCatching { ChatGptExchange.accept(p, target, source, emptyMap(), reply(other)) }.isFailure)
        assertTrue(runCatching { ChatGptExchange.accept(p, target, source + " ", emptyMap(), reply(p)) }.isFailure)
        assertTrue(runCatching { ChatGptExchange.accept(p, "/other/settings.ini", source, emptyMap(), reply(p)) }.isFailure)
        assertTrue(runCatching { ChatGptExchange.accept(p, target, source, emptyMap(), reply(p).replace("Speed=2","Missing=2")) }.isFailure)
    }

    @Test fun translationDraftSnapshotAndPlaceholderValidation() {
        val rows = "id\ttext\na\tHello {name}\nb\tBye"
        val edits = mutableMapOf("b" to "안녕히")
        val p = ChatGptExchange.create(target, GameAgent.Request("한글화", "tsv", rows, edits))
        val valid = """{"exchange_id":"${p.id}","action":"translate","explanation":"번역","row.a":"안녕 {name}"}"""
        val result = ChatGptExchange.accept(p, target, rows, edits, valid) as GameAgent.Result.Translation
        assertEquals(mapOf("a" to "안녕 {name}", "b" to "안녕히"), result.edits)
        assertTrue(runCatching { ChatGptExchange.accept(p, target, rows, edits, valid.replace("{name}", "")) }.isFailure)
        edits["b"] = "새 번역"
        assertTrue(runCatching { ChatGptExchange.accept(p, target, rows, edits, valid) }.isFailure)
    }
}
