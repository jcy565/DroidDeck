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
    @Test fun discoversPersistedBackupAndRejectsTruncatedRecord() {
        val root = temp.newFolder()
        val backups = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        val backup = GamePatchStore.apply(root, file.name, "x=1".toByteArray(), "x=2".toByteArray(), backups)
        assertEquals(backup, GamePatchStore.findMatchingBackup(root, file.name, backups))
        java.io.RandomAccessFile(backup, "rw").use { it.setLength(it.length() - 1) }
        assertNull(GamePatchStore.findMatchingBackup(root, file.name, backups))
        assertEquals("x=2", file.readText())
    }

    @Test fun ignoresBackupAfterExternalChange() {
        val root = temp.newFolder()
        val backups = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        GamePatchStore.apply(root, file.name, "x=1".toByteArray(), "x=2".toByteArray(), backups)
        file.writeText("x=3")
        assertNull(GamePatchStore.findMatchingBackup(root, file.name, backups))
    }

    @Test fun rejectsSameLengthBackupCorruption() {
        val root = temp.newFolder()
        val backups = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        val backup = GamePatchStore.apply(root, file.name, file.readBytes(), "x=2".toByteArray(), backups)
        java.io.RandomAccessFile(backup, "rw").use { it.seek(it.length() - 1); it.write(65) }
        assertNull(GamePatchStore.findMatchingBackup(root, file.name, backups))
        assertThrows(IllegalArgumentException::class.java) { GamePatchStore.restore(root, backup) }
        assertEquals("x=2", file.readText())
    }

    @Test fun preservesPosixModeOnApplyAndRestore() {
        val root = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        val mode = java.nio.file.attribute.PosixFilePermissions.fromString("rw-r-----")
        java.nio.file.Files.setPosixFilePermissions(file.toPath(), mode)
        val backup = GamePatchStore.apply(root, file.name, file.readBytes(), "x=2".toByteArray(), temp.newFolder())
        assertEquals(mode, java.nio.file.Files.getPosixFilePermissions(file.toPath()))
        GamePatchStore.restore(root, backup)
        assertEquals(mode, java.nio.file.Files.getPosixFilePermissions(file.toPath()))
    }

    @Test fun recognizesGraphicsFailureAndDoesNotGuessFromBlackScreen() {
        assertTrue(LocalGameAdvisor.diagnose("VK_ERROR_DEVICE_LOST").contains("장치 연결 손실"))
        assertTrue(LocalGameAdvisor.diagnose("black screen").contains("확정하기 어렵"))
    }

    @Test fun proposesOnlyOneLowerResolutionWithTheSameAspectRatio() {
        assertEquals("1280x720", LocalGameAdvisor.optimize("[Render]\nResolution=1920x1080").changes.single().after)
        assertThrows(IllegalArgumentException::class.java) { LocalGameAdvisor.optimize("[Render]\nResolution=800x600") }
    }

    @Test fun refusesOversizeAndBinaryInputs() {
        assertThrows(IllegalArgumentException::class.java) { GamePatchStore.readLimited(ByteArray(1048577).inputStream()) }
        assertThrows(IllegalArgumentException::class.java) { LocalGameAdvisor.decode(byteArrayOf(0, 65)) }
    }

    @Test fun folderAnalysisSkipsLinksAndFlagsCandidatesWithoutDeleting() {
        val root = temp.newFolder()
        root.resolve("cache").mkdir()
        root.resolve("cache/game.txt").writeText("abc")
        root.resolve("game.txt").writeText("xyz")
        java.nio.file.Files.createSymbolicLink(root.resolve("loop").toPath(), root.toPath())
        val report = FolderUsage.inspect(root)
        assertEquals(2, report.files)
        assertEquals(6L, report.bytes)
        assertEquals(3L, report.temporaryBytes)
        assertEquals(1, report.candidates.size)
        assertTrue(FolderUsage.inspect(root, 1).partial)
    }

    @Test fun readsAnExistingLegacyBackup() {
        val root = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=2") }
        val backup = temp.newFile()
        java.io.DataOutputStream(backup.outputStream()).use {
            it.writeUTF(root.canonicalPath)
            it.writeUTF(file.name)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            it.writeUTF(digest.joinToString("") { byte -> "%02x".format(byte) })
            it.writeInt(3)
            it.write("x=1".toByteArray())
        }
        GamePatchStore.restore(root, backup)
        assertEquals("x=1", file.readText())
    }

    @Test fun importsOneBoundedTranslationAndRejectsOtherArchiveLayouts() {
        fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
            val buffer = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(buffer).use { out ->
                for ((name, bytes) in entries) {
                    out.putNextEntry(java.util.zip.ZipEntry(name))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
            return buffer.toByteArray()
        }
        val entry = "ko.txt" to "번역".toByteArray()
        assertEquals("번역", LocalGameAdvisor.replacement("ko.txt", "pack.zip", zip(entry).inputStream()))
        for (bad in listOf(zip("../ko.txt" to entry.second), zip(entry, "a.exe" to byteArrayOf()),
                zip("ko.txt" to ByteArray(1048577)), zip("ko.txt" to byteArrayOf(0, 10)))) {
            assertThrows(IllegalArgumentException::class.java) {
                LocalGameAdvisor.replacement("ko.txt", "pack.zip", bad.inputStream())
            }
        }
    }
}
