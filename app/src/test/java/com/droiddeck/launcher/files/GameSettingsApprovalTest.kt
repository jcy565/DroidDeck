package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class GameSettingsApprovalTest {
    @get:Rule val temp = TemporaryFolder()
    private val original = "[Render]\r\nResolution=1920x1080\r\n"
    private val mode = GameFileAnalysis.Mode.RESOLUTION_720

    @Test fun approvedPreviewBacksUpAndRestoresExactBytes() {
        val root = temp.newFolder("game")
        val backupDir = temp.newFolder("backup")
        val file = root.resolve("settings.ini").apply { writeText(original) }
        val plan = GameSettingsApproval.prepare(root, file, mode)
        assertEquals(original, file.readText())
        val backup = GameSettingsApproval.apply(root, file, plan, backupDir) {}
        assertEquals("[Render]\r\nResolution=1280x720\r\n", file.readText())
        assertEquals(backup, GamePatchStore.findMatchingBackup(root, "settings.ini", backupDir))
        GamePatchStore.restore(root, backup, "settings.ini")
        assertEquals(original, file.readText())
    }

    @Test fun changedFileRequiresNewApproval() {
        val root = temp.newFolder("game")
        val backupDir = temp.newFolder("backup")
        val file = root.resolve("settings.ini").apply { writeText(original) }
        val plan = GameSettingsApproval.prepare(root, file, mode)
        val changed = original + "; external edit\r\n"
        file.writeText(changed)
        assertThrows(IllegalArgumentException::class.java) {
            GameSettingsApproval.apply(root, file, plan, backupDir) {}
        }
        assertEquals(changed, file.readText())
        assertTrue(backupDir.listFiles()!!.isEmpty())
    }

    @Test fun differentRootOrTargetRequiresNewApproval() {
        val root = temp.newFolder("game")
        val other = temp.newFolder("other")
        val backupDir = temp.newFolder("backup")
        val file = root.resolve("settings.ini").apply { writeText(original) }
        val outside = other.resolve("settings.ini").apply { writeText(original) }
        val sameBytes = root.resolve("other.ini").apply { writeText(original) }
        val plan = GameSettingsApproval.prepare(root, file, mode)
        assertThrows(IllegalArgumentException::class.java) { GameSettingsApproval.prepare(root, outside, mode) }
        assertThrows(IllegalArgumentException::class.java) { GameSettingsApproval.apply(other, outside, plan, backupDir) {} }
        assertThrows(IllegalArgumentException::class.java) { GameSettingsApproval.apply(root, sameBytes, plan, backupDir) {} }
        assertTrue(backupDir.listFiles()!!.isEmpty())
        assertEquals(original, outside.readText())
    }

    @Test fun rejectsLinkedDirectoryAndLinkedFile() {
        val root = temp.newFolder("game")
        val outside = temp.newFolder("other")
        val file = outside.resolve("settings.ini").apply { writeText(original) }
        Files.createSymbolicLink(root.resolve("linked").toPath(), outside.toPath())
        Files.createSymbolicLink(root.resolve("linked.ini").toPath(), file.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            GameSettingsApproval.prepare(root, root.resolve("linked/settings.ini"), mode)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameSettingsApproval.prepare(root, root.resolve("linked.ini"), mode)
        }
        assertEquals(original, file.readText())
    }

    @Test fun budgetDenialPreventsWriting() {
        val root = temp.newFolder("game")
        val backupDir = temp.newFolder("backup")
        val file = root.resolve("settings.ini").apply { writeText(original) }
        val plan = GameSettingsApproval.prepare(root, file, mode)
        assertThrows(IllegalStateException::class.java) {
            GameSettingsApproval.apply(root, file, plan, backupDir) { error("Session active") }
        }
        assertEquals(original, file.readText())
        assertTrue(backupDir.listFiles()!!.isEmpty())
    }
}
