package com.droiddeck.launcher.files

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageDraftStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val draft = ImageDraftStore.Draft("こんにちは\nHello", "안녕하세요",
        ImageTranslationInput.Script.JAPANESE, ImageTranslationInput.Rotation.CLOCKWISE_90)

    @Test fun streamedIdentityCanLoadAndDeleteAPreviouslySavedDraft() {
        val image = temporary.newFile("old-menu.png")
        image.outputStream().use {
            it.write(ByteArray(8192) { index -> index.toByte() })
            it.write('X'.code)
        }
        val id = ImageDraftStore.identity(image)
        assertEquals("45b5d4a71815afb033c3faf9aa879114a977cab0d47aa123183a6a1756953474", id.digest)
        val digest = MessageDigest.getInstance("SHA-256")
        val pathHash = digest.digest(image.canonicalPath.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        val oldName = "$pathHash-${id.digest}"
        val directory = temporary.newFolder("old-records")
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use {
            listOf("DDIMAGE1", oldName, draft.script.name, draft.rotation.name, draft.source, draft.translated)
                .forEach(it::writeUTF)
        }
        val bytes = body.toByteArray()
        val record = File(directory, "$oldName.draft")
        record.writeBytes(digest.digest(bytes) + bytes)
        assertEquals(draft, ImageDraftStore.load(directory, id))
        assertTrue(ImageDraftStore.deleteIfUnchanged(directory, image, id, draft))
        assertFalse(record.exists())
        assertEquals(id, ImageDraftStore.identity(image))
    }

    @Test fun savesAndReopensOnlyExactImageRevision() {
        val root = temporary.newFolder("game")
        val image = File(root, "menu.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val directory = temporary.newFolder("drafts")
        val id = ImageDraftStore.identity(image)
        assertNull(ImageDraftStore.load(directory, id))
        ImageDraftStore.save(directory, image, id, draft)
        assertEquals(draft, ImageDraftStore.load(directory, id))
        val other = File(root, "other.png").apply { writeBytes(image.readBytes()) }
        assertNull(ImageDraftStore.load(directory, ImageDraftStore.identity(other)))
        image.writeBytes(byteArrayOf(3, 2, 1))
        assertNull(ImageDraftStore.load(directory, ImageDraftStore.identity(image)))
        assertTrue(runCatching { ImageDraftStore.save(directory, image, id, draft) }.isFailure)
        assertEquals(draft, ImageDraftStore.load(directory, id))
        assertArrayEquals(byteArrayOf(3, 2, 1), image.readBytes())
    }

    @Test fun savesRegionCoordinatesAndPartialEdits() {
        val image = temporary.newFile("regions.png").apply { writeBytes(byteArrayOf(1, 2)) }
        val directory = temporary.newFolder("regions")
        val id = ImageDraftStore.identity(image)
        val result = ImageTextRegions.build("Menu\\nStart".replace("\\n", "\n"), 100, 80, sequenceOf(
            ImageTextRegions.Box(1, 2, 40, 20) to "Menu",
            ImageTextRegions.Box(2, 30, 50, 55) to "Start"))
        val edited = ImageDraftStore.Draft(result.text, "메뉴", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, result.copy(partial = true), mapOf(1 to "메뉴"))
        ImageDraftStore.save(directory, image, id, edited)
        assertEquals(edited, ImageDraftStore.load(directory, id))
        assertArrayEquals(byteArrayOf(1, 2), image.readBytes())
        ImageDraftStore.save(directory, image, id, draft)
        assertEquals(draft, ImageDraftStore.load(directory, id))
    }

    @Test fun rejectsInvalidRegionDraftWithoutReplacingSavedRecord() {
        val image = temporary.newFile("invalid.png").apply { writeBytes(byteArrayOf(1)) }
        val directory = temporary.newFolder("invalid")
        val id = ImageDraftStore.identity(image)
        val result = ImageTextRegions.build("Menu", 100, 80,
            sequenceOf(ImageTextRegions.Box(1, 2, 40, 20) to "Menu"))
        val valid = ImageDraftStore.Draft(result.text, "", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, result, mapOf(1 to "메뉴"))
        ImageDraftStore.save(directory, image, id, valid)
        val bad = listOf(valid.copy(edits = mapOf(2 to "메뉴")),
            valid.copy(rotation = ImageTranslationInput.Rotation.CLOCKWISE_90),
            valid.copy(source = "other"),
            valid.copy(regions = null),
            valid.copy(regions = result.copy(width = 1)),
            valid.copy(regions = result.copy(regions = result.regions + result.regions)))
        bad.forEach { assertTrue(runCatching { ImageDraftStore.save(directory, image, id, it) }.isFailure) }
        assertEquals(valid, ImageDraftStore.load(directory, id))
    }

    @Test fun rejectsDamagedOrOversizedRecords() {
        val image = temporary.newFile("menu.png").apply { writeBytes(byteArrayOf(1)) }
        val directory = temporary.newFolder("drafts")
        val id = ImageDraftStore.identity(image)
        ImageDraftStore.save(directory, image, id, draft)
        val record = directory.listFiles()!!.single()
        val bytes = record.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        record.writeBytes(bytes)
        assertTrue(runCatching { ImageDraftStore.load(directory, id) }.isFailure)
        record.writeBytes(ByteArray(65537))
        assertTrue(runCatching { ImageDraftStore.load(directory, id) }.isFailure)
        assertTrue(runCatching { ImageDraftStore.save(directory, image, id, draft.copy(source = "")) }.isFailure)
    }

    @Test fun reviewedReplacementRequiresTheSameSavedDraftAndImageRevision() {
        val image = temporary.newFile("replace.png").apply { writeBytes(byteArrayOf(1)) }
        val directory = temporary.newFolder("replace-drafts")
        val id = ImageDraftStore.identity(image)
        val edited = draft.copy(translated = "현재 편집")
        val newer = draft.copy(translated = "다른 편집")
        assertTrue(ImageDraftStore.saveIfAbsent(directory, image, id, draft))
        assertFalse(ImageDraftStore.saveIfAbsent(directory, image, id, edited))
        assertTrue(ImageDraftStore.replaceIfUnchanged(directory, image, id, draft, newer))
        assertFalse(ImageDraftStore.replaceIfUnchanged(directory, image, id, draft, edited))
        assertEquals(newer, ImageDraftStore.load(directory, id))
        image.writeBytes(byteArrayOf(2))
        assertTrue(runCatching {
            ImageDraftStore.replaceIfUnchanged(directory, image, id, newer, edited)
        }.isFailure)
        assertEquals(newer, ImageDraftStore.load(directory, id))
        image.writeBytes(byteArrayOf(1))
        assertTrue(ImageDraftStore.deleteIfUnchanged(directory, image, id, newer))
        assertFalse(ImageDraftStore.replaceIfUnchanged(directory, image, id, newer, edited))
        assertNull(ImageDraftStore.load(directory, id))
    }

    @Test fun deletionPreservesConcurrentEditsOtherRevisionsAndImageBytes() {
        val image = temporary.newFile("delete.png").apply { writeBytes(byteArrayOf(1)) }
        val directory = temporary.newFolder("delete-drafts")
        val first = ImageDraftStore.identity(image)
        ImageDraftStore.save(directory, image, first, draft)
        val edited = draft.copy(translated = "수정본")
        ImageDraftStore.save(directory, image, first, edited)
        assertFalse(ImageDraftStore.deleteIfUnchanged(directory, image, first, draft))
        assertEquals(edited, ImageDraftStore.load(directory, first))

        image.writeBytes(byteArrayOf(2))
        val second = ImageDraftStore.identity(image)
        ImageDraftStore.save(directory, image, second, draft)
        assertTrue(runCatching { ImageDraftStore.deleteIfUnchanged(directory, image, first, edited) }.isFailure)
        assertTrue(ImageDraftStore.deleteIfUnchanged(directory, image, second, draft))
        assertNull(ImageDraftStore.load(directory, second))
        assertFalse(ImageDraftStore.deleteIfUnchanged(directory, image, second, draft))
        assertEquals(edited, ImageDraftStore.load(directory, first))
        assertArrayEquals(byteArrayOf(2), image.readBytes())
    }

    @Test fun nonDirectoryStorageIsNeverTreatedAsMissingDraft() {
        val image = temporary.newFile("menu-storage.png").apply { writeBytes(byteArrayOf(1)) }
        val storage = temporary.newFile("not-a-directory").apply { writeText("preserve") }
        val id = ImageDraftStore.identity(image)
        assertTrue(runCatching { ImageDraftStore.load(storage, id) }.isFailure)
        assertTrue(runCatching { ImageDraftStore.save(storage, image, id, draft) }.isFailure)
        assertTrue(runCatching { ImageDraftStore.saveIfAbsent(storage, image, id, draft) }.isFailure)
        assertTrue(runCatching { ImageDraftStore.deleteIfUnchanged(storage, image, id, draft) }.isFailure)
        assertEquals("preserve", storage.readText())
        val missing = File(temporary.root, "new-drafts")
        assertNull(ImageDraftStore.load(missing, id))
        assertTrue(ImageDraftStore.saveIfAbsent(missing, image, id, draft))
        assertEquals(draft, ImageDraftStore.load(missing, id))
    }

    @Test fun batchSaveCannotReplaceCorruptOrNonRegularDraft() {
        val image = temporary.newFile("batch-menu.png").apply { writeBytes(byteArrayOf(1)) }
        val directory = temporary.newFolder("batch-drafts")
        val id = ImageDraftStore.identity(image)
        assertTrue(ImageDraftStore.saveIfAbsent(directory, image, id, draft))
        val record = directory.listFiles()!!.single()
        val damaged = byteArrayOf(1, 2, 3)
        record.writeBytes(damaged)
        assertFalse(ImageDraftStore.saveIfAbsent(directory, image, id, draft))
        assertArrayEquals(damaged, record.readBytes())
        assertTrue(record.delete())
        assertTrue(record.mkdir())
        assertFalse(ImageDraftStore.saveIfAbsent(directory, image, id, draft))
        assertTrue(runCatching { ImageDraftStore.load(directory, id) }.isFailure)
        assertTrue(record.isDirectory)
        assertTrue(record.delete())
        Files.createSymbolicLink(record.toPath(), File(directory, "missing").toPath())
        assertFalse(ImageDraftStore.saveIfAbsent(directory, image, id, draft))
        assertTrue(Files.isSymbolicLink(record.toPath()))
        assertArrayEquals(byteArrayOf(1), image.readBytes())
    }

    @Test fun rejectsSymbolicLinkRecordsAndPreservesTheirTargets() {
        val image = temporary.newFile("menu.png").apply { writeBytes(byteArrayOf(1)) }
        val directory = temporary.newFolder("drafts")
        val id = ImageDraftStore.identity(image)
        ImageDraftStore.save(directory, image, id, draft)
        val record = directory.listFiles()!!.single()
        val target = temporary.newFile("target").apply { writeText("original") }
        assertTrue(record.delete())
        Files.createSymbolicLink(record.toPath(), target.toPath())
        assertTrue(runCatching { ImageDraftStore.load(directory, id) }.isFailure)
        assertTrue(runCatching { ImageDraftStore.save(directory, image, id, draft) }.isFailure)
        assertTrue(runCatching { ImageDraftStore.deleteIfUnchanged(directory, image, id, draft) }.isFailure)
        assertTrue(Files.isSymbolicLink(record.toPath()))
        assertEquals("original", target.readText())
    }
}
