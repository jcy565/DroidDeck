package com.droiddeck.launcher.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageDraftStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val draft = ImageDraftStore.Draft("こんにちは\nHello", "안녕하세요",
        ImageTranslationInput.Script.JAPANESE, ImageTranslationInput.Rotation.CLOCKWISE_90)

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
        assertEquals("original", target.readText())
    }
}
