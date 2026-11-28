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

    @Test fun configPreviewUsesTheSameValidatedValuesForRewriting() {
        val source = "[Render]\r\nResolution=1920x1080\r\nName=old\r\n[Other]\nResolution=800x600\n"
        val reviewed = linkedMapOf("Resolution" to "1280x720", "Name" to "한글 😀")
        val inconsistent = object : Map<String, String> by reviewed {
            override fun get(key: String): String? = "unsafe\nExtra=1"
        }
        val preview = GameConfigPreview.preview(source, "Render", inconsistent)
        assertEquals(source.replace("1920x1080", "1280x720").replace("Name=old", "Name=한글 😀"), preview.proposed)
        assertEquals(reviewed.values.toList(), preview.changes.map { it.after })
        val invalid = object : Map<String, String> by reviewed {
            override val entries get() = mapOf("Resolution" to "720\nExtra=1", "Name" to "x").entries
        }
        assertThrows(IllegalArgumentException::class.java) {
            GameConfigPreview.preview(source, "Render", invalid)
        }
    }

    @Test fun configAndZipRejectMalformedUnicodeBeforeEncoding() {
        val source = "[Render]\nName=old"
        val preview = GameConfigPreview.preview(source, "Render", mapOf("Name" to "한글 😀"))
        for (code in 0xD800..0xDFFF) {
            val bad = "bad" + code.toChar()
            assertThrows(IllegalArgumentException::class.java) {
                GameConfigPreview.preview(source, "Render", mapOf("Name" to bad))
            }
            assertThrows(IllegalArgumentException::class.java) {
                GameConfigPreview.preview(source + bad, "Render", mapOf("Name" to "new"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                GamePatchAuthor.zip("$bad.ini", preview)
            }
            assertThrows(IllegalArgumentException::class.java) {
                GamePatchAuthor.zip("menu.ini", preview.copy(proposed = bad))
            }
            assertThrows(IllegalArgumentException::class.java) {
                GamePatchAuthor.zip("menu.ini", preview.copy(original = bad))
            }
        }
        val zip = GamePatchAuthor.zip("한글😀.ini", preview)
        assertEquals(preview.proposed, LocalGameAdvisor.replacement("한글😀.ini", "patch.zip", zip.inputStream()))
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

    @Test fun checksummedBackupRejectsMetadataDamageAndPreservesOtherTargets() {
        val root = temp.newFolder()
        val backups = temp.newFolder()
        val a = root.resolve("a.ini").apply { writeText("x=1") }
        val b = root.resolve("b.ini").apply { writeText("x=2") }
        val backup = GamePatchStore.apply(root, a.name, a.readBytes(), "x=2".toByteArray(), backups)
        val bytes = backup.readBytes()
        assertEquals("DDPATCH3", java.io.DataInputStream(bytes.inputStream()).use { it.readUTF() })
        for (index in bytes.indices) {
            val damaged = bytes.clone().also { it[index] = (it[index].toInt() xor 1).toByte() }
            backup.writeBytes(damaged)
            assertTrue("damage at $index", runCatching { GamePatchStore.restore(root, backup) }.isFailure)
            assertEquals("x=2", a.readText())
            assertEquals("x=2", b.readText())
        }
        backup.writeBytes(bytes)
        GamePatchStore.restore(root, backup, a.name)
        assertEquals("x=1", a.readText())
        assertEquals("x=2", b.readText())
    }

    @Test fun restoresAnExistingVersionTwoBackup() {
        val root = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=2") }
        val backup = temp.newFile()
        val original = "x=1".toByteArray()
        fun hash(bytes: ByteArray) = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        java.io.DataOutputStream(backup.outputStream()).use {
            it.writeUTF("DDPATCH2")
            it.writeUTF(hash(original))
            it.writeUTF(root.canonicalPath)
            it.writeUTF(file.name)
            it.writeUTF(hash(file.readBytes()))
            it.writeInt(original.size)
            it.write(original)
        }
        GamePatchStore.restore(root, backup, file.name)
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun invalidBackupDestinationLeavesSourceAndExistingDataUntouched() {
        val root = temp.newFolder()
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        val invalid = temp.newFile().apply { writeText("preserve") }
        val original = file.readBytes()
        val proposed = "x=2".toByteArray()
        assertTrue(runCatching {
            GamePatchStore.apply(root, file.name, original, proposed, invalid)
        }.isFailure)
        assertEquals("preserve", invalid.readText())
        assertArrayEquals(original, file.readBytes())
        val storage = temp.newFolder()
        val alias = temp.root.resolve("backup-alias")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), storage.toPath())
        assertTrue(runCatching {
            GamePatchStore.apply(root, file.name, original, proposed, alias)
        }.isFailure)
        assertTrue(storage.listFiles()!!.isEmpty())
        assertTrue(java.nio.file.Files.isSymbolicLink(alias.toPath()))
        assertArrayEquals(original, file.readBytes())
        assertEquals(listOf(file), root.listFiles()!!.toList())
    }

    @Test fun backupStorageMustBeSeparateFromGameDirectory() {
        val root = temp.newFolder("game")
        val file = root.resolve("game.ini").apply { writeText("x=1") }
        val original = file.readBytes()
        val proposed = "x=2".toByteArray()
        val nested = root.resolve("new-backups")
        val alias = temp.root.resolve("game-alias")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), root.toPath())
        for (invalid in listOf(root, temp.root, nested, alias.resolve("new-backups"))) {
            val before = root.listFiles()!!.toSet()
            assertThrows(IllegalArgumentException::class.java) {
                GamePatchStore.apply(root, file.name, original, proposed, invalid)
            }
            assertArrayEquals(original, file.readBytes())
            assertEquals(before, root.listFiles()!!.toSet())
            assertFalse(nested.exists())
        }
        val sibling = temp.root.resolve("game-backups")
        val backup = GamePatchStore.apply(root, file.name, original, proposed, sibling)
        assertEquals(sibling, backup.parentFile)
        GamePatchStore.restore(root, backup, file.name)
        assertArrayEquals(original, file.readBytes())
    }

    @Test fun refusesAnInputStreamThatNeverMakesProgress() {
        val stalled = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertThrows(IllegalArgumentException::class.java) { GamePatchStore.readLimited(stalled) }
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

    @Test fun balancedAndBatteryPlansTouchOnlyTheirRequestedGroup() {
        val ini = "[/Script/Engine.GameUserSettings]\nResolutionSizeX=1920\nResolutionSizeY=1080\nFrameRateLimit=0.0"
        val balanced = LocalGameAdvisor.optimize(ini, "900p 균형")
        assertEquals(listOf("1600", "900"), balanced.changes.map { it.after })
        val capped = LocalGameAdvisor.optimize(ini, "배터리 30 FPS")
        assertEquals(listOf("FrameRateLimit"), capped.changes.map { it.key })
        assertThrows(IllegalArgumentException::class.java) { LocalGameAdvisor.optimize(ini.replace("=0.0", "=24"), "배터리") }
    }
}
