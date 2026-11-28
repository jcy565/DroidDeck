package com.droiddeck.launcher.files

import android.app.Application
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SharedAgentReplyInboxTest {
    @Test fun replacementIsExplicitIdempotentAndBoundToReviewedState() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.noBackupFilesDir, "ai-exchange/checkpoint.bin")
        SharedAgentReply.discard(context)
        ExchangeCheckpoint.discard(file)
        val saved = ExchangeCheckpoint.Saved(UUID.randomUUID().toString(), "Translate", 0, "a".repeat(64))
        fun reply(text: String) = """{"exchange_id":"${saved.id}","action":"advice","explanation":"$text"}"""
        try {
            ExchangeCheckpoint.save(file, saved)
            val first = reply("first")
            val second = reply("second")
            val third = reply("third")
            assertNull(SharedAgentReply.receive(context, first))
            val revision = SharedAgentReply.revision.value
            assertNull(SharedAgentReply.receive(context, first))
            assertEquals(revision, SharedAgentReply.revision.value)
            val review = requireNotNull(SharedAgentReply.receive(context, second))
            val competing = requireNotNull(SharedAgentReply.receive(context, third))
            assertEquals(first, review.previous)
            assertEquals(second, review.incoming)
            assertEquals(first, SharedAgentReply.load(context, saved.id))
            assertEquals(revision, SharedAgentReply.revision.value)
            SharedAgentReply.replace(context, review)
            assertEquals(second, SharedAgentReply.load(context, saved.id))
            assertTrue(runCatching { SharedAgentReply.replace(context, competing) }.isFailure)
            assertEquals(second, SharedAgentReply.load(context, saved.id))
            val changedRequest = requireNotNull(SharedAgentReply.receive(context, third))
            ExchangeCheckpoint.save(file, saved.copy(instruction = "Different instruction"))
            assertTrue(runCatching { SharedAgentReply.replace(context, changedRequest) }.isFailure)
            assertEquals(second, SharedAgentReply.load(context, saved.id))
            assertTrue(ExchangeCheckpoint.clear(file, saved.id))
            assertTrue(runCatching { SharedAgentReply.replace(context, changedRequest) }.isFailure)
            assertNull(SharedAgentReply.load(context, saved.id))
        } finally {
            SharedAgentReply.discard(context)
            ExchangeCheckpoint.discard(file)
        }
    }
}
