package com.droiddeck.launcher.compatibility

import android.content.Context
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.droiddeck.launcher.ui.createRobolectricComposeRule
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class SettingsRecoveryReviewPanelTest {
    @get:Rule val compose = createRobolectricComposeRule()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val key = "displayResolution.steam"

    @Before fun setup() {
        assertTrue(context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit())
        SettingsBackup.apply(context, key, null, "1280x720")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openConfirmation() {
        compose.setContent { SettingsRecoveryReviewPanel() }
        compose.onNodeWithText("설정 및 백업 조회").performClick()
        awaitText("Steam 출력 해상도 복원 검토")
        compose.onNodeWithText("Steam 출력 해상도 복원 검토").performClick()
    }

    @Test fun cancelKeepsTheBackupAndConfirmationRestoresIt() {
        openConfirmation()
        compose.onNodeWithText("취소").performClick()
        assertEquals("1280x720", SettingsBackup.raw(context, key))
        assertNotNull(SettingsBackup.review(context, key))
        compose.onNodeWithText("Steam 출력 해상도 복원 검토").performClick()
        compose.onNodeWithText("복원하기").performClick()
        awaitText("Steam 출력 해상도: 백업 없음")
        assertNull(SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))
    }

    private fun openArchiveConfirmation() {
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        compose.setContent { SettingsRecoveryReviewPanel() }
        compose.onNodeWithText("설정 및 백업 조회").performClick()
        awaitText("Steam 출력 해상도 백업 보관 검토")
        compose.onNodeWithText("Steam 출력 해상도 백업 보관 검토").performClick()
    }

    @Test fun confirmedArchiveKeepsTheCurrentValueAndProtectsTheOccupiedSlot() {
        openArchiveConfirmation()
        compose.onNodeWithText("취소").performClick()
        assertNull(SettingsBackup.archivedReview(context, key))
        val reviewed = requireNotNull(SettingsBackup.review(context, key))
        compose.onNodeWithText("Steam 출력 해상도 백업 보관 검토").performClick()
        compose.onNodeWithText("현재 값 유지하고 보관").performClick()
        awaitText("이전 기록 보관됨: 덮어쓰기 금지")
        assertEquals("1600x900", SettingsBackup.raw(context, key))
        assertEquals(reviewed, SettingsBackup.archivedReview(context, key))
        assertNull(SettingsBackup.review(context, key))

        SettingsBackup.apply(context, key, "1600x900", "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        compose.onNodeWithText("설정 및 백업 조회").performClick()
        awaitText("Steam 출력 해상도: 현재 값과 충돌: 복구 보류")
        compose.onNodeWithText("Steam 출력 해상도 백업 보관 검토").assertDoesNotExist()
        assertEquals(reviewed, SettingsBackup.archivedReview(context, key))
    }

    @Test fun archiveRequiresFreshConfirmationAfterChangingAwayAndBack() {
        openArchiveConfirmation()
        SettingsBackup.writeNormalResolution(context, "steam", "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        compose.onNodeWithText("현재 값 유지하고 보관").performClick()
        awaitText("보관 결과를 확인할 수 없습니다. 현재 설정과 복구 기록을 다시 확인해 주세요.")
        assertNull(SettingsBackup.archivedReview(context, key))
        assertNotNull(SettingsBackup.review(context, key))
    }

    @Test fun sessionStartedAfterReviewKeepsTheSettingAndBackup() {
        openConfirmation()
        val review = SettingsBackup.review(context, key)
        requireNotNull(SessionDriverGate.shared.tryAcquire(SessionDriverGate.Kind.SESSION)).use {
            compose.onNodeWithText("복원하기").performClick()
            awaitText("복원 결과를 확인할 수 없습니다. 게임을 종료하고 현재 설정과 복구 기록을 다시 확인해 주세요.")
        }
        assertEquals("1280x720", SettingsBackup.raw(context, key))
        assertEquals(review, SettingsBackup.review(context, key))
    }

    @Test fun valueChangedWithDialogOpenIsPreserved() {
        openConfirmation()
        SettingsBackup.writeNormalResolution(context, "steam", "1600x900")
        compose.onNodeWithText("복원하기").performClick()
        awaitText("복원 결과를 확인할 수 없습니다. 게임을 종료하고 현재 설정과 복구 기록을 다시 확인해 주세요.")
        assertEquals("1600x900", SettingsBackup.raw(context, key))
        compose.onNodeWithText("설정 및 백업 조회").performClick()
        awaitText("Steam 출력 해상도: 현재 값과 충돌: 복구 보류")
        compose.onNodeWithText("Steam 출력 해상도 복원 검토").assertDoesNotExist()
    }
}
