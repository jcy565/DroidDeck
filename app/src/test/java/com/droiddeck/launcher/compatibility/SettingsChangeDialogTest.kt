package com.droiddeck.launcher.compatibility

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class SettingsChangeDialogTest {
    @get:Rule val compose = createRobolectricComposeRule()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val key = "displayResolution.steam"

    @Before fun setup() {
        assertTrue(context.getSharedPreferences("session", Context.MODE_PRIVATE).edit().clear().commit())
        compose.setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) { SettingsRecoveryReviewPanel() }
        }
        compose.onNodeWithText("설정 및 백업 조회").performClick()
        awaitText("Steam 출력 해상도 변경")
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun choose(label: String, value: String) {
        compose.onNodeWithText("$label 변경").performScrollTo().performClick()
        compose.onNodeWithText(value).performClick()
        awaitText("선택한 값: $value")
    }

    @Test fun steamResolutionCanBePreviewedCancelledAppliedAndRestored() {
        val label = "Steam 출력 해상도"
        choose(label, "1280x720")
        compose.onNodeWithText("취소").performClick()
        assertNull(SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))

        choose(label, "1600x900")
        compose.onNodeWithText("백업 후 적용").performClick()
        awaitText("$label 복원 검토")
        assertEquals("1600x900", SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.raw(context, "displayResolution.desktop"))
        compose.onNodeWithText("$label 변경").assertDoesNotExist()
        compose.onNodeWithText("$label 복원 검토").performClick()
        compose.onNodeWithText("복원하기").performClick()
        awaitText("$label 변경")
        assertNull(SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))
    }

    @Test fun bridgeNoOpIsDisabledAndApprovalStoresABoolean() {
        choose("Proton DXVK 브리지", "끄기")
        compose.onNodeWithText("백업 후 적용").assertIsNotEnabled()
        compose.onNodeWithText("켜기").performClick()
        awaitText("선택한 값: 켜기")
        compose.onNodeWithText("백업 후 적용").performClick()
        awaitText("Proton DXVK 브리지 복원 검토")
        assertTrue(context.getSharedPreferences("session", Context.MODE_PRIVATE).getBoolean("bridgeProtonDxvk", false))
    }

    @Test fun anOrdinaryWriteAfterPreviewIsPreserved() {
        choose("Steam 출력 해상도", "1280x720")
        SettingsBackup.writeNormalResolution(context, "steam", "900p")
        compose.onNodeWithText("백업 후 적용").performClick()
        awaitText("미리보기 이후 설정이 바뀌었습니다. 현재 값으로 다시 미리보기를 열어 주세요.")
        assertEquals("900p", SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))
    }

    @Test fun pendingBackupCreatedWhilePreviewIsOpenIsPreserved() {
        choose("데스크톱 출력 해상도", "1600x900")
        val desktop = "displayResolution.desktop"
        val prior = SettingsBackup.apply(context, desktop, null, "1280x720")
        compose.onNodeWithText("백업 후 적용").performClick()
        awaitText("이 설정에 기존 백업이 있습니다. 백업 상태를 확인해 주세요.")
        awaitText("데스크톱 출력 해상도 복원 검토")
        assertEquals(prior, SettingsBackup.review(context, desktop))
        assertEquals("1280x720", SettingsBackup.raw(context, desktop))
    }

    @Test fun startingASessionAfterPreviewPreventsWrites() {
        choose("Steam 출력 해상도", "1280x720")
        requireNotNull(SessionDriverGate.shared.tryAcquire(SessionDriverGate.Kind.SESSION)).use {
            compose.onNodeWithText("백업 후 적용").performClick()
            awaitText("게임 실행 또는 다른 설정 변경이 끝난 뒤 다시 시도해 주세요.")
        }
        assertNull(SettingsBackup.raw(context, key))
        assertNull(SettingsBackup.review(context, key))
    }
}
