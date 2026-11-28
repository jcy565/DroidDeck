package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class GameFileAnalysisTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun logDiagnosisDoesNotWriteOrReturnRawLog() {
        val file = folder.newFile("game.log")
        val source = "private marker\nVK_ERROR_OUT_OF_DEVICE_MEMORY\n"
        file.writeText(source)
        val result = GameFileAnalysis.analyze(file, GameFileAnalysis.Mode.LOG)
        assertTrue(result.diagnosis!!.contains("VK_ERROR_OUT_OF_DEVICE_MEMORY"))
        assertFalse(result.diagnosis.contains("private marker"))
        assertTrue(result.changes.isEmpty())
        assertEquals(source, file.readText())
    }

    @Test fun configPreviewDoesNotWrite() {
        val file = folder.newFile("game.ini")
        val source = "[Render]\r\nResolution=1920x1080\r\n"
        file.writeText(source)
        val result = GameFileAnalysis.analyze(file, GameFileAnalysis.Mode.RESOLUTION_720)
        assertNull(result.diagnosis)
        assertEquals(listOf(GameConfigPreview.Change("Resolution", "1920x1080", "1280x720")), result.changes)
        assertEquals(source, file.readText())
    }

    @Test fun wrongExtensionOrModeRejected() {
        val file = folder.newFile("game.txt")
        assertThrows(IllegalArgumentException::class.java) {
            GameFileAnalysis.analyze(file, GameFileAnalysis.Mode.LOG)
        }
        val ini = folder.newFile("game.cfg")
        assertThrows(IllegalArgumentException::class.java) {
            GameFileAnalysis.analyze(ini, GameFileAnalysis.Mode.LOG)
        }
    }

    @Test fun cancellationStopsAnalysis() {
        val file = folder.newFile("game.log")
        file.writeText("VK_ERROR_DEVICE_LOST")
        var checks = 0
        assertThrows(IllegalStateException::class.java) {
            GameFileAnalysis.analyze(file, GameFileAnalysis.Mode.LOG) { check(++checks < 3) }
        }
        assertEquals(3, checks)
        assertEquals("VK_ERROR_DEVICE_LOST", file.readText())
    }

    @Test fun rejectsOversizeFileAndSymbolicLink() {
        val file = folder.newFile("large.log")
        file.writeBytes(ByteArray(1024 * 1024 + 1))
        assertThrows(IllegalArgumentException::class.java) {
            GameFileAnalysis.analyze(file, GameFileAnalysis.Mode.LOG)
        }
        val link = folder.root.toPath().resolve("linked.log")
        Files.createSymbolicLink(link, file.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            GameFileAnalysis.analyze(link.toFile(), GameFileAnalysis.Mode.LOG)
        }
    }
}
