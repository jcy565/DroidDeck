package com.droiddeck.launcher.compatibility

import org.junit.Assert.*
import org.junit.Test

class SmartMemoryTest {
    @Test fun releasesOnlyUnderPressureWhileIdle() {
        for (level in listOf(10, 15, 40, 60, 80)) {
            assertTrue(SmartMemory.shouldRelease(level, true))
            assertFalse(SmartMemory.shouldRelease(level, false))
        }
        for (level in listOf(0, 5, 20)) assertFalse(SmartMemory.shouldRelease(level, true))
    }

    @Test fun stoppedScanDoesNotContinueCounting() {
        val root = java.nio.file.Files.createTempDirectory("ddscan").toFile()
        try {
            repeat(10) { root.resolve("$it.txt").writeText("sample") }
            var visits = 0
            assertThrows(IllegalStateException::class.java) {
                com.droiddeck.launcher.files.FolderUsage.inspect(root) { check(++visits < 3) }
            }
            assertEquals(3, visits)
            assertEquals(10, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
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
