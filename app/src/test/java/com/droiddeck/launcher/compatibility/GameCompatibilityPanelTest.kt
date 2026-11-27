package com.droiddeck.launcher.compatibility

import android.app.ActivityManager
import android.content.Context
import androidx.compose.ui.test.*
import com.droiddeck.launcher.stores.Store
import com.droiddeck.launcher.stores.StoreGameSidecar
import com.droiddeck.launcher.ui.createRobolectricComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class GameCompatibilityPanelTest {
    @get:Rule val compose = createRobolectricComposeRule()
    @get:Rule val temp = TemporaryFolder()

    @Test fun recordingAfterAStoreUpdateShowsBothVersionsAndTheOlderUnknownVersion() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("ai_game_run_history.db")
        shadowOf(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .setMemoryInfo(ActivityManager.MemoryInfo().apply { totalMem = 1024; availMem = 512 })
        val folder = temp.newFolder("game")
        val id = "gog:123"
        GameRunHistory(context).use {
            it.record(GameRunHistory.Observation(id, null, GameRunHistory.Outcome.BLACK_SCREEN, 1))
        }
        val installed = StoreGameSidecar(Store.GOG, "123", "Example", "game.exe", installVersion = "1.2")
        installed.write(folder)
        compose.setContent { GameCompatibilityPanel(id, folder) }
        fun awaitText(text: String) {
            compose.waitUntil(5_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
        }
        awaitText("버전별 실행 기록 보기")
        compose.onNodeWithText("버전별 실행 기록 보기").performScrollTo().performClick()
        awaitText("검은 화면 · 버전 미확인")
        compose.onNodeWithText("게임 충돌").performScrollTo().performClick()
        awaitText("게임 충돌 · 설치 버전 1.2")
        installed.copy(installVersion = "1.3").write(folder)
        compose.onNodeWithText("플레이 가능").performScrollTo().performClick()
        awaitText("플레이 가능 · 설치 버전 1.3")
        compose.onNodeWithText("게임 충돌 · 설치 버전 1.2", substring = true).assertExists()
    }
}
