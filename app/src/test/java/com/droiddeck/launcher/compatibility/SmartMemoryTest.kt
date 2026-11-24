package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class SmartMemoryTest {
    @Test fun normalMemory() {
        val report = SmartMemory.describe(GameplayBudget.MemorySnapshot(200, 1000, 100, false))
        assertEquals(200L, report.availableBytes!!)
        assertEquals(1000L, report.totalBytes!!)
        assertTrue(report.guidance.contains("부족 신호가 없습니다"))
    }
    @Test fun lowMemory() {
        val report = SmartMemory.describe(GameplayBudget.MemorySnapshot(50, 1000, 100, true))
        assertTrue(report.guidance.contains("분석을 보류"))
    }
    @Test fun missingReading() {
        val report = SmartMemory.describe(null)
        assertNull(report.availableBytes)
        assertNull(report.totalBytes)
        assertTrue(report.guidance.contains("확인할 수 없습니다"))
    }
}
