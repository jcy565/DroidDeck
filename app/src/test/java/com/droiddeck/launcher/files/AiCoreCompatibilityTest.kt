package com.droiddeck.launcher.files

import com.droiddeck.ai.core.AiCore
import org.junit.Assert.*
import org.junit.Test

class AiCoreCompatibilityTest {
    @Test fun hostAndDraftCompatibilityAreIndependent() {
        assertEquals(AiCore.Status.COMPATIBLE, AiCore.compatibility(1, 1, 1))
        for (api in listOf(-1, 0, 2, Int.MAX_VALUE)) {
            assertEquals(AiCore.Status.HOST_API_MISMATCH, AiCore.compatibility(api, 1, 1))
        }
        for ((min, max) in listOf(0 to 0, 2 to 2, 2 to 1)) {
            assertEquals(AiCore.Status.DRAFT_SCHEMA_MISMATCH, AiCore.compatibility(1, min, max))
        }
        assertTrue(AiHostBridge.availability().enabled)
        assertEquals(AiCore.version(), AiHostBridge.availability().version)
    }

    @Test fun exchangeSnapshotRejectsTargetSourceAndDraftChanges() {
        val edits = mutableMapOf("a" to "기존")
        val binding = AiCore.Exchange("file", "source", edits)
        assertEquals(AiCore.ExchangeStatus.MATCH, binding.check("file", "source", edits))
        assertEquals(AiCore.ExchangeStatus.SOURCE_CHANGED, binding.check("other", "source", edits))
        assertEquals(AiCore.ExchangeStatus.SOURCE_CHANGED, binding.check("file", "changed", edits))
        edits["a"] = "수정"
        assertEquals(AiCore.ExchangeStatus.DRAFT_CHANGED, binding.check("file", "source", edits))
        assertEquals(AiCore.ExchangeStatus.MATCH, binding.check("file", "source", mapOf("a" to "기존")))
        assertNotEquals(binding.id(), AiCore.Exchange("file", "source", edits).id())
    }
}
