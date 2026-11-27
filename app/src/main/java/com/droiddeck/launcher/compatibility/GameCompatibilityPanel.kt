package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Host must supply the selected game's stable, platform-qualified identifier. */
@Composable
internal fun GameCompatibilityPanel(gameId: String, gameVersion: String?, modifier: Modifier = Modifier, profileKey: String? = null) {
    key(gameId, gameVersion, profileKey) {
        val context = LocalContext.current.applicationContext
        val service = remember(context) { GameCompatibilityService(context) }
        val scope = rememberCoroutineScope()
        var report by remember { mutableStateOf<GameCompatibilityService.Report?>(null) }
        var busy by remember { mutableStateOf(true) }
        var currentVersionOnly by remember { mutableStateOf(gameVersion != null) }
        var error by remember { mutableStateOf<String?>(null) }

        suspend fun refresh() {
            report = service.report(gameId, gameVersion = gameVersion.takeIf { currentVersionOnly })
        }

        LaunchedEffect(service) {
            try {
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "실행 기록을 읽지 못했습니다."
            } finally {
                busy = false
            }
        }

        Column(modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("게임 호환성 기록")
            Text("최근 20건의 관측 기록입니다. 실행 성공은 플레이 가능을 의미하지 않습니다.")
            Text(if (gameVersion == null) "설치 버전: 확인되지 않음" else "설치 버전 (최근 조회): $gameVersion")
            if (gameVersion != null) {
                Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
                    if (!busy) {
                        busy = true
                        currentVersionOnly = !currentVersionOnly
                        report = null
                        error = null
                        scope.launch {
                            try {
                                refresh()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                error = "실행 기록을 읽지 못했습니다."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }) { Text(if (currentVersionOnly) "현재 버전 기록 · 전체 버전 보기" else "전체 버전 기록 · 현재 버전 보기") }
            }
            report?.let { current ->
                Text(current.description)
                current.advice.forEach { Text("• " + it) }
            }
            error?.let { Text(it) }
            if (busy) Text("처리 중")
            SettingsExperimentPreviewPanel()
            RuntimeFexPreviewPanel(profileKey)
            SettingsRecoveryReviewPanel()
            Text("직접 확인한 실행 결과를 기록하세요.")
            listOf(
                GameRunHistory.Outcome.PLAYABLE to "플레이 가능",
                GameRunHistory.Outcome.BLACK_SCREEN to "검은 화면",
                GameRunHistory.Outcome.CRASH to "게임 충돌",
                GameRunHistory.Outcome.UNKNOWN to "판정 보류"
            ).forEach { (outcome, label) ->
                Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
                    if (!busy) {
                        busy = true
                        error = null
                        scope.launch {
                            var saved = false
                            try {
                                service.record(GameRunHistory.Observation(gameId, gameVersion,
                                    outcome, System.currentTimeMillis()))
                                saved = true
                                refresh()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = if (saved) "기록은 저장됐지만 조회에 실패했습니다."
                                    else "기록 저장에 실패했습니다."
                            } finally {
                                busy = false
                            }
                        }
                    }
                }) { Text(label) }
            }
        }
    }
}
