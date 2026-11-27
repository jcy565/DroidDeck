package com.droiddeck.launcher.compatibility

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

        suspend fun refresh(saved: Boolean = false) {
            report = null
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

        LaunchedEffect(service) {
            try {
                refresh()
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
