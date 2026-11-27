package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
        val observedVersion = gameVersion?.takeIf(GameHistoryQuery::validVersion)
        var currentVersionOnly by remember { mutableStateOf(observedVersion != null) }
        var confirmedVersion by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        suspend fun refresh() {
            report = service.report(gameId, gameVersion = observedVersion.takeIf { currentVersionOnly })
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
            Text(if (observedVersion == null) "설치 버전: 확인되지 않음" else "설치 버전 (최근 조회): $observedVersion")
            if (observedVersion != null) {
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
            Text(if (currentVersionOnly) "현재 버전 최근 20건 (버전 미확인 제외)"
                else "전체 버전 최근 20건 (버전 미확인 포함)")
            report?.let { current ->
                if (currentVersionOnly && current.observations.isEmpty()) Text("현재 버전의 기록이 없습니다.")
                if (!currentVersionOnly && current.observations.isNotEmpty())
                    Text("전체 버전 통합 관측 요약 (현재 버전의 판정이 아님)")
                Text(current.description)
                if (!currentVersionOnly && current.observations.isNotEmpty()) {
                    val groups = GameHistoryByVersion.summarize(current.observations)
                    Text("조회된 기록의 버전별 분류 (최신 관측 순)")
                    groups.take(5).forEach { group ->
                        Text("버전: " + (group.version ?: "미확인"))
                        Text(GameHistorySummary.describe(group.summary))
                    }
                    if (groups.size > 5) Text("나머지 버전 분류 ${groups.size - 5}개")
                    Text("아래 확인 방법은 가장 최근 관측 기록 기준입니다: " +
                        (current.observations.first().gameVersion ?: "버전 미확인"))
                    Text("이전 버전의 결과로 현재 버전의 호환성을 판단할 수 없습니다.")
                }
                current.advice.forEach { Text("• " + it) }
            }
            error?.let { Text(it) }
            if (busy) Text("처리 중")
            SettingsExperimentPreviewPanel()
            RuntimeFexPreviewPanel(profileKey)
            SettingsRecoveryReviewPanel()
            Text("직접 확인한 실행 결과를 기록하세요.")
            if (observedVersion != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = confirmedVersion, enabled = !busy,
                        onCheckedChange = { confirmedVersion = it })
                    Text("실행한 게임의 버전이 최근 조회값과 일치함을 확인했습니다.")
                }
            }
            if (!confirmedVersion) Text("버전 미확인으로 기록합니다.")
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
                        val recordedVersion = GameHistoryQuery.observationVersion(observedVersion, confirmedVersion)
                        confirmedVersion = false
                        scope.launch {
                            var saved = false
                            try {
                                service.record(GameRunHistory.Observation(gameId, recordedVersion,
                                    outcome, System.currentTimeMillis()))
                                saved = true
                                if (recordedVersion == null) {
                                    currentVersionOnly = false
                                    report = null
                                }
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
