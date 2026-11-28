package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class SharedAgentReplyTest {
    private val id = "12345678-1234-1234-1234-123456789abc"
    private val reply = """{"exchange_id":"$id","action":"advice","explanation":"검토"}"""

    @Test fun acceptsOnlyBoundTextAndOptionalSingleJsonFence() {
        assertEquals(reply, SharedAgentReply.normalize(reply, id))
        assertEquals(reply, SharedAgentReply.normalize("```json\n" + reply + "\n```", id))
        for (invalid in listOf("https://chatgpt.com/share/example", reply + "\nmore",
            reply.replace(id, "different"), reply.replace("advice", "shell"),
            reply.replace("\"action\":\"advice\"", "\"action\":\"advice\",\"action\":\"mod\""),
            "x".repeat(GameAgent.MAX_OUTPUT_CHARS + 33))) {
            assertTrue(runCatching { SharedAgentReply.normalize(invalid, id) }.isFailure)
        }
    }
}
