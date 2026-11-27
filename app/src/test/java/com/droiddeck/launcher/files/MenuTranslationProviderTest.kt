package com.droiddeck.launcher.files

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MenuTranslationProviderTest {
    @Test fun exactKnownPhrasesOnly() = runBlocking {
        val provider = MenuTranslationProvider()
        assertEquals("게임 시작", provider.translate("Start Game"))
        assertEquals("설정", provider.translate("SETTINGS"))
        for (text in listOf("Start Game {name}", "Start Game %s", " Start Game",
            "Start Game now", "Custom dialogue", "설정")) {
            assertEquals(text, provider.translate(text))
        }
        provider.close()
    }

    @Test fun enginePreservesExistingDraftAndUntranslatedDialogue() = runBlocking {
        val source = "id\ttext\na\tStart Game\nb\tSettings\nc\tCustom dialogue\nd\tExit Game"
        val existing = mapOf("b" to "환경 설정")
        val result = TranslationEngine.run(source, existing, allowed = { true },
            providerFactory = { MenuTranslationProvider() })
        assertEquals(mapOf("a" to "게임 시작", "b" to "환경 설정", "d" to "게임 종료"), result.edits)
        assertEquals(2, result.added)
        assertEquals(1, result.rejected)
        assertEquals(1, result.skipped)
        assertEquals(TranslationEngine.Stop.FILE_COMPLETE, result.stop)
        assertEquals(1, existing.size)
        assertTrue(GamePatchAuthor.translation(source, result.edits).proposed.contains("c\tCustom dialogue"))
    }

    @Test fun resumesAtNextWindow() = runBlocking {
        val source = (1..26).joinToString("\n", "id\ttext\n") { "id$it\tStart Game" }
        val first = TranslationEngine.run(source, emptyMap(), allowed = { true },
            providerFactory = { MenuTranslationProvider() })
        assertEquals(25, first.nextIndex)
        assertEquals(25, first.added)
        val second = TranslationEngine.run(source, first.edits, startIndex = first.nextIndex,
            allowed = { true }, providerFactory = { MenuTranslationProvider() })
        assertEquals(26, second.edits.size)
        assertEquals(TranslationEngine.Stop.FILE_COMPLETE, second.stop)
    }
}
