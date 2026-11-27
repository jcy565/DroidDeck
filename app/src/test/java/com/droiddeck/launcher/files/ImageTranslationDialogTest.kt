package com.droiddeck.launcher.files

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import java.io.File
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.droiddeck.launcher.ui.createRobolectricComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImageTranslationDialogTest {
    @get:Rule val compose = createRobolectricComposeRule()
    @get:Rule val temporary = TemporaryFolder()
    private var closes = 0

    private fun image() = temporary.newFile("menu.png").apply { writeBytes(byteArrayOf(1)) }

    private fun openEditor(file: File = image()) {
        compose.setContent { ImageTranslationDialog(file) { closes++ } }
        val ready = hasText("닫기") and hasClickAction() and isEnabled()
        compose.waitUntil(5_000) { compose.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun cancellingDiscardPreservesTextAndExplicitDiscardClosesOnce() {
        openEditor()
        val source = compose.onNode(hasSetTextAction() and hasText("추출한 글자 · 수정 가능"))
        source.performScrollTo().performTextReplacement("Start")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("계속 편집").performClick()
        compose.onNode(hasSetTextAction() and hasText("Start")).assertExists()
        compose.runOnIdle { assertEquals(0, closes) }
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("저장하지 않고 닫기").performClick()
        compose.runOnIdle { assertEquals(1, closes) }
    }

    @Test fun editingTheSourceInvalidatesTranslationOnlyWhenTheTextChanges() {
        openEditor()
        val source = compose.onNode(hasSetTextAction() and hasText("추출한 글자 · 수정 가능"))
        val translation = compose.onNode(hasSetTextAction() and hasText("한국어 번역문 붙여넣기 · 수정 가능"))
        source.performScrollTo().performTextReplacement("Start")
        translation.performScrollTo().performTextReplacement("시작")
        source.performScrollTo().performTextReplacement("Start")
        compose.onNode(hasSetTextAction() and hasText("시작")).assertExists()
        source.performTextReplacement("Continue")
        compose.onNode(hasSetTextAction() and hasText("시작")).assertDoesNotExist()
    }

    @Test fun reviewedDeletionShowsRegionTranslationsAndPreservesANewerSave() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        shadowOf(manager).setMemoryInfo(ActivityManager.MemoryInfo().apply {
            totalMem = 8L shl 30
            availMem = 2L shl 30
            threshold = 128L shl 20
        })
        val file = image()
        val id = ImageDraftStore.identity(file)
        val directory = File(context.noBackupFilesDir, "image-translation-drafts")
        val regions = ImageTextRegions.build("Start", 100, 80,
            sequenceOf(ImageTextRegions.Box(0, 0, 20, 10) to "Start"))
        val saved = ImageDraftStore.Draft("Start", "", ImageTranslationInput.Script.LATIN,
            ImageTranslationInput.Rotation.NONE, regions, mapOf(1 to "시작"))
        ImageDraftStore.save(directory, file, id, saved)
        openEditor(file)
        compose.onNodeWithText("저장된 초안 삭제…").performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("영역 1 · Start\n시작")).fetchSemanticsNodes().isNotEmpty()
        }
        val newer = saved.copy(edits = mapOf(1 to "게임 시작"))
        ImageDraftStore.save(directory, file, id, newer)
        compose.onNodeWithText("초안 삭제").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("저장된 초안이 바뀌었거나 이미 삭제됐습니다. 다시 확인하세요."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(newer, ImageDraftStore.load(directory, id))
    }

    @Test fun undoingAnEditAllowsClosingWithoutDiscardConfirmation() {
        openEditor()
        val translation = compose.onNode(hasSetTextAction() and hasText("한국어 번역문 붙여넣기 · 수정 가능"))
        translation.performScrollTo().performTextReplacement("시작")
        translation.performTextReplacement("")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("저장하지 않고 닫기").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, closes) }
    }
}
