package com.droiddeck.launcher.compatibility

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.files.GamePatchStore
import com.droiddeck.launcher.files.LocalGameAdvisor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        var draft by remember { mutableStateOf<GameObservationDraft.Draft?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var showTimeline by remember { mutableStateOf(false) }
        var focusStatus by remember { mutableStateOf<String?>(null) }
        var logReport by remember { mutableStateOf<GameCompatibilityService.LogReport?>(null) }
        var pickerHistory by remember { mutableStateOf<GameCompatibilityService.Report?>(null) }

        suspend fun refresh(saved: Boolean = false) {
            report = null
            logReport = null
            try {
                report = service.report(gameId, gameVersion = observedVersion.takeIf { currentVersionOnly })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = HistoryReadFeedback.failure(saved, e is HistoryReadDeferred)
            }
        }

        fun requestRefresh(toggleVersion: Boolean = false) {
            if (busy || draft != null) return
            busy = true
            error = null
            report = null
            if (toggleVersion) currentVersionOnly = !currentVersionOnly
            scope.launch {
                try {
                    refresh()
                } finally {
                    busy = false
                }
            }
        }

        fun confirmObservation(selected: GameObservationDraft.Draft, attribution: GameObservationDraft.Attribution) {
            if (busy || draft != selected) return
            val observation = try {
                GameObservationDraft.confirm(selected, gameId, observedVersion, attribution, System.currentTimeMillis())
            } catch (_: IllegalArgumentException) {
                error = "게임 또는 버전을 확인한 뒤 다시 기록해 주세요."
                draft = null
                return
            }
            draft = null
            busy = true
            error = null
            scope.launch {
                var saved = false
                try {
                    service.record(observation)
                    saved = true
                    if (observation.gameVersion == null) {
                        currentVersionOnly = false
                        report = null
                    }
                    refresh(saved = true)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = if (saved) HistoryReadFeedback.failure(true, false)
                    else "기록 저장에 실패했습니다."
                } finally {
                    busy = false
                }
            }
        }

        val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val selectedHistory = pickerHistory
            pickerHistory = null
            if (selectedHistory == null || uri == null || report !== selectedHistory) {
                busy = false
            } else {
                scope.launch {
                    try {
                        require(uri.scheme == "content")
                        logReport = withContext(Dispatchers.IO) {
                            if (!GameplayBudget.allowsOptionalAnalysis(context)) throw HistoryReadDeferred()
                            val bytes = context.contentResolver.openInputStream(uri)?.use(GamePatchStore::readLimited)
                                ?: error("로그 파일을 읽을 수 없습니다.")
                            val log = LocalGameAdvisor.decode(bytes)
                            if (!GameplayBudget.allowsOptionalAnalysis(context)) throw HistoryReadDeferred()
                            GameCompatibilityService.combine(selectedHistory, log)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        error = if (e is HistoryReadDeferred) "게임 실행 또는 메모리 상태 때문에 로그 분석을 보류했습니다."
                            else "로그 분석 실패: " + (e.message ?: "지원하는 UTF-8 텍스트 로그를 선택하세요.")
                    } finally {
                        busy = false
                    }
                }
            }
        }

        LaunchedEffect(service) {
            try {
                refresh()
            } finally {
                busy = false
            }
        }

        Column(modifier = modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("게임 집중 보호 · 실행·설치 중 선택적 AI 분석 제한")
            TextButton(enabled = !busy && draft == null, onClick = {
                focusStatus = GameplayBudget.admission(context).guidance
            }) { Text("현재 집중 보호 상태 확인") }
            focusStatus?.let { Text("조회 시점: " + it) }
            Text("게임 성능의 명시적 측정은 별도로 사용할 수 있습니다. 시스템 앱 강제 종료나 게임 설정 변경은 하지 않습니다.")
            Text("게임 호환성 기록")
            Text("최근 20건의 관측 기록입니다. 실행 성공은 플레이 가능을 의미하지 않습니다.")
            Text(if (observedVersion == null) "설치 버전: 확인되지 않음" else "설치 버전 (최근 조회): $observedVersion")
            if (observedVersion != null) {
                Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && draft == null, onClick = {
                    requestRefresh(toggleVersion = true)
                }) { Text(if (currentVersionOnly) "현재 버전 기록 · 전체 버전 보기" else "전체 버전 기록 · 현재 버전 보기") }
            }
            Text(if (currentVersionOnly) "현재 버전 최근 20건 (버전 미확인 제외)"
                else "전체 버전 최근 20건 (버전 미확인 포함)")
            report?.let { current ->
                if (!currentVersionOnly && current.observations.isNotEmpty())
                    Text("전체 버전 통합 관측 요약 (현재 버전의 판정이 아님)")
                if (currentVersionOnly && current.observations.isEmpty()) Text("현재 버전의 기록이 없습니다.")
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
                Text("현재 게임의 실행 로그와 위 기록을 함께 검토할 수 있습니다. 같은 게임의 UTF-8 텍스트 로그만 직접 선택하세요. 버전이나 실행 세션의 일치는 자동 확인하지 않습니다.")
                TextButton(enabled = !busy && draft == null, onClick = {
                    if (!busy && report === current) {
                        busy = true
                        error = null
                        logReport = null
                        pickerHistory = current
                        try { logPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
                        catch (_: Exception) {
                            pickerHistory = null
                            busy = false
                            error = "문서 선택기를 열지 못했습니다."
                        }
                    }
                }) { Text("이 게임의 로그 선택 · 실행 기록과 함께 분석") }
                logReport?.takeIf { it.history === current }?.let { combined ->
                    Text("선택한 로그 분석 · 기록과 별개 증거")
                    Text(combined.logDiagnosis)
                    Text(combined.limitation)
                    Text("로그는 이 화면에서만 분석하며 저장하거나 전송하지 않습니다.")
                    TextButton(enabled = !busy, onClick = { logReport = null }) { Text("로그 분석 닫기") }
                }
                if (current.observations.isNotEmpty()) {
                    TextButton(onClick = { showTimeline = !showTimeline }) {
                        Text(if (showTimeline) "실행 기록 상세 닫기" else "실행 기록 상세 보기")
                    }
                    if (showTimeline) {
                        Text("최근 저장 순서 · 이 기록은 개별 관측 결과이며 성능을 보장하지 않습니다.")
                        val formatter = java.text.DateFormat.getDateTimeInstance(
                            java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT
                        )
                        GameHistoryTimeline.rows(current.observations).forEach { row ->
                            Text(formatter.format(java.util.Date(row.recordedAt)) +
                                " · 버전: " + (row.version ?: "미확인") + " · " + row.label)
                        }
                    }
                }
            }
            error?.let { Text(it) }
            TextButton(enabled = !busy && draft == null, onClick = { requestRefresh() }) {
                Text("실행 기록 다시 조회")
            }
            if (busy) Text("처리 중")
            com.droiddeck.launcher.ui.GamePerformanceHistoryPanel(gameId, enabled = !busy && draft == null)
            SettingsExperimentPreviewPanel(gameId)
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
                    if (!busy && draft == null) {
                        try {
                            draft = GameObservationDraft.prepare(gameId, observedVersion, outcome)
                            error = null
                        } catch (_: IllegalArgumentException) {
                            error = "기록 대상 정보를 확인할 수 없습니다."
                        }
                    }
                }) { Text(label) }
            }
        }
        draft?.let { selected ->
            val label = when (selected.outcome) {
                GameRunHistory.Outcome.PLAYABLE -> "플레이 가능"
                GameRunHistory.Outcome.BLACK_SCREEN -> "검은 화면"
                GameRunHistory.Outcome.CRASH -> "게임 충돌"
                else -> "판정 보류"
            }
            AlertDialog(
                onDismissRequest = { draft = null },
                title = { Text("실행 기록 저장 확인") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("관측 결과: " + label)
                        Text("최근 확인한 설치 버전: " + (selected.installedVersion ?: "미확인"))
                        Text("실제 실행한 버전이 이 설치 정보와 같은 경우에만 해당 버전으로 기록하세요.")
                        if (selected.installedVersion != null) {
                            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                                confirmObservation(selected, GameObservationDraft.Attribution.OBSERVED_INSTALL)
                            }) { Text("확인된 설치 버전으로 기록") }
                        }
                        Button(modifier = Modifier.fillMaxWidth(), onClick = {
                            confirmObservation(selected, GameObservationDraft.Attribution.UNKNOWN)
                        }) { Text("버전 미확인으로 기록") }
                    }
                },
                confirmButton = { TextButton(onClick = { draft = null }) { Text("취소") } }
            )
        }
    }
}
