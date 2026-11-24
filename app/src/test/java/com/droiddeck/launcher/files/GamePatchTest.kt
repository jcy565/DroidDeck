package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class GamePatchTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun preservesLineEndingsAndOtherSections() {
        val original = "[Render]\r\nResolution=1920x1080\r\n[Other]\r\nResolution=800x600\r\n"
        val p = GameConfigPreview.preview(original, "Render", mapOf("Resolution" to "1280x720"))
        assertEquals(original.replace("1920x1080", "1280x720"), p.proposed)
        assertEquals(1, p.changes.size)
    }

    @Test fun rejectsAmbiguousOrMissingSettings() {
        for (input in listOf("x=1\nx=2", "y=1", "x=1 ; comment")) {
            assertThrows(IllegalArgumentException::class.java) {
                GameConfigPreview.preview(input, null, mapOf("x" to "3"))
            }
        }
    }

    @Test fun rejectsMultilineValues() {
        assertThrows(IllegalArgumentException::class.java) {
            GameConfigPreview.preview("x=1", null, mapOf("x" to "2\ny=3"))
        }
    }

    @Test fun backupAndRestoreRoundTrip() {
        val root = temp.newFolder()
        val backups = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        val backup = GamePatchStore.apply(root, file.name, "x=1".toByteArray(), "x=2".toByteArray(), backups)
        assertEquals("x=2", file.readText())
        GamePatchStore.restore(root, backup)
        assertEquals("x=1", file.readText())
    }

    @Test fun refusesStalePreviewAndRestore() {
        val root = temp.newFolder()
        val backups = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        assertThrows(IllegalArgumentException::class.java) {
            GamePatchStore.apply(root, file.name, "x=0".toByteArray(), "x=2".toByteArray(), backups)
        }
        val backup = GamePatchStore.apply(root, file.name, "x=1".toByteArray(), "x=2".toByteArray(), backups)
        file.writeText("x=3")
        assertThrows(IllegalArgumentException::class.java) { GamePatchStore.restore(root, backup) }
        assertEquals("x=3", file.readText())
    }

    @Test fun rejectsTraversal() {
        val root = temp.newFolder()
        val backups = temp.newFolder()
        assertThrows(IllegalArgumentException::class.java) {
            GamePatchStore.apply(root, "../game.ini", byteArrayOf(), byteArrayOf(), backups)
        }
    }
}
