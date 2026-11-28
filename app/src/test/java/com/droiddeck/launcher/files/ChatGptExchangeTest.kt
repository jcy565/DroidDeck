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

    @Test fun pendingAndRestoredDraftsAreImmutableAndCheckOneSnapshot() {
        val rows = "id\ttext\na\tHello {name}\nb\tBye\nc\tMenu"
        val reviewed = linkedMapOf("b" to "잘 가", "c" to "메뉴")
        val caller = reviewed.toMutableMap()
        val p = ChatGptExchange.create(target, GameAgent.Request("번역", "tsv", rows, caller))
        caller["b"] = "changed"
        assertEquals(reviewed, p.request.existing)
        assertTrue(runCatching { (p.request.existing as MutableMap)["b"] = "unchecked" }.isFailure)
        val response = """{"exchange_id":"${p.id}","action":"translate","explanation":"번역","row.a":"안녕 {name}"}"""
        assertTrue(runCatching { ChatGptExchange.accept(p, target, rows, caller, response) }.isFailure)
        val inconsistent = object : Map<String, String> by reviewed {
            override fun get(key: String): String? = "unchecked"
        }
        val saved = ChatGptExchange.checkpoint(p, target, rows, reviewed)
        assertEquals(saved, ChatGptExchange.checkpoint(p, target, rows, inconsistent))
        val restored = ChatGptExchange.restore(saved, target, "tsv", rows, reviewed)
        assertTrue(runCatching { (restored.request.existing as MutableMap).clear() }.isFailure)
        val result = ChatGptExchange.accept(restored, target, rows, inconsistent, response) as GameAgent.Result.Translation
        assertEquals(reviewed + ("a" to "안녕 {name}"), result.edits)
        assertTrue(runCatching { ChatGptExchange.accept(restored, target, rows, result.edits, response) }.isFailure)
        assertTrue(runCatching { ChatGptExchange.create(target, GameAgent.Request("번역", "tsv", rows,
            reviewed + ("b" to "bad\uD800"))) }.isFailure)
    }

    @Test fun savedRequestRestoresExactIdAndRejectsChangedSource() {
        val p = ChatGptExchange.create(target, request)
        val saved = ChatGptExchange.checkpoint(p, target, source, emptyMap())
        val resumed = ChatGptExchange.restore(saved, target, "ini", source, emptyMap())
        assertEquals(p.id, resumed.id)
        assertEquals(p.prompt, resumed.prompt)
        assertTrue(ChatGptExchange.accept(resumed, target, source, emptyMap(), reply(p)) is GameAgent.Result.Patch)
        assertTrue(runCatching { ChatGptExchange.restore(saved, target, "ini", source + " ", emptyMap()) }.isFailure)
        assertTrue(runCatching { ChatGptExchange.restore(saved, "/other/settings.ini", "ini", source, emptyMap()) }.isFailure)
    }
}
