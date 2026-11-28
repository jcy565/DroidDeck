package com.droiddeck.launcher.files

import org.junit.Assert.*
import org.junit.Test

class ImageTranslationInputTest {
    @Test fun limitsInputSize() {
        val input = ImageTranslationInput
        assertArrayEquals(byteArrayOf(1, 2), input.read(byteArrayOf(1, 2).inputStream()))
        assertEquals(input.MAX_BYTES, input.read(ByteArray(input.MAX_BYTES).inputStream()).size)
        assertTrue(runCatching { input.read(ByteArray(0).inputStream()) }.isFailure)
        assertTrue(runCatching { input.read(ByteArray(input.MAX_BYTES + 1).inputStream()) }.isFailure)
    }

    @Test fun rejectsStalledImageInput() {
        val stalled = object : java.io.InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertTrue(runCatching { ImageTranslationInput.read(stalled) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun sampleSizeLimitsDecodedPixels() {
        val input = ImageTranslationInput
        assertEquals(1, input.sampleSize(2048, 2048))
        assertEquals(2, input.sampleSize(4096, 4096))
        assertEquals(4, input.sampleSize(8192, 4096))
        assertTrue(runCatching { input.sampleSize(8192, 8192) }.isFailure)
        assertTrue(runCatching { input.sampleSize(0, 1) }.isFailure)
        assertTrue(runCatching { input.sampleSize(Int.MAX_VALUE, Int.MAX_VALUE) }.isFailure)
    }

    @Test fun textIsBoundedAndPromptPreservesSource() {
        val input = ImageTranslationInput
        val source = "Hello %s\nWorld\t123"
        assertEquals(source, input.validateText(source))
        assertTrue(input.prompt(source).endsWith(source))
        assertTrue(runCatching { input.validateText(" ") }.isFailure)
        assertTrue(runCatching { input.validateText("x".repeat(input.MAX_TEXT + 1)) }.isFailure)
        assertTrue(runCatching { input.validateText("a\u0000b") }.isFailure)
    }
    @Test fun supportsSelectedScriptsAndQuarterTurnRotations() {
        val input = ImageTranslationInput
        assertEquals(listOf("LATIN", "JAPANESE", "CHINESE"), ImageTranslationInput.Script.values().map { it.name })
        assertEquals(listOf(0, 90, 180, 270), ImageTranslationInput.Rotation.values().map { it.degrees })
        for (source in listOf("こんにちは", "你好", "游戏设置", "ゲーム開始", "English 日本語 中文")) {
            assertEquals(source, input.validateText(source))
            assertTrue(input.prompt(source).endsWith(source))
        }
    }
}
