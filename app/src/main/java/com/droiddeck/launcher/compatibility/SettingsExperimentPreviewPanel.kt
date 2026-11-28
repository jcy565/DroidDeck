package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.session.SessionDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-requested read-only comparisons. No setting is applied or queued. */
@Composable
internal fun SettingsExperimentPreviewPanel(gameId: String? = null) {
    val uiContext = LocalContext.current
    val context = uiContext.applicationContext
    val scope = rememberCoroutineScope()
    var options by remember(context) { mutableStateOf<List<DroidDeckSettingsPreview.Change>?>(null) }
    var request by remember(context) { mutableStateOf<SettingsApplyApproval.Request?>(null) }
    var busy by remember(context) { mutableStateOf(false) }
    var error by remember(context) { mutableStateOf<String?>(null) }
    var bookmark by remember(context, gameId) { mutableStateOf<SavedOptimizationChoice.Choice?>(null) }
    var bookmarkMessage by remember(context, gameId) { mutableStateOf("") }

    LaunchedEffect(context, gameId) {
        if (gameId != null) {
            try {
                bookmark = withContext(Dispatchers.IO) { SavedOptimizationChoice.load(context, gameId) }
            } catch (e: CancellationException) { throw e }
              catch (_: Exception) { bookmarkMessage = "저장한 후보를 읽지 못했습니다." }
        }
    }

    fun bookmarkTask(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        bookmarkMessage = ""
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { bookmarkMessage = "저장 후보 처리 실패. 다시 확인하세요." }
            finally { busy = false }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("출력 해상도·DXVK 설정 미리보기")
        if (gameId != null) {
            Text("이 게임의 최적화 후보 북마크 · 실제 설정은 전역 또는 실행 모드 범위입니다.")
            bookmarkMessage.takeIf { it.isNotBlank() }?.let { Text(it) }
            bookmark?.let { saved ->
                Text("저장 후보: " + saved.scope + " · " + saved.proposed)
                TextButton(enabled = !busy, onClick = {
                    bookmarkTask {
                        val panel = SessionDisplay.panelSize(uiContext)
                        val refreshed = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            SavedOptimizationChoice.refresh(context, saved, panel)
                        }
                        options = null
                        request = if (DroidDeckSettingsPreview.changed(refreshed))
                            SettingsApplyApproval.prepare(refreshed, panel) else null
                        bookmarkMessage = if (request == null) "이미 현재 설정과 같습니다." else "현재 설정 기준으로 다시 검토하세요."
                    }
                }) { Text("저장 후보 다시 검토") }
                TextButton(enabled = !busy, onClick = {
                    bookmarkTask {
                        withContext(Dispatchers.IO) { SavedOptimizationChoice.remove(context, gameId) }
                        bookmark = null
                        bookmarkMessage = "북마크를 삭제했습니다. 실제 설정은 그대로입니다."
                    }
                }) { Text("저장 후보 삭제") }
            }
        }
        Text("Steam·데스크톱 또는 전체 게임에 영향을 주는 설정입니다. 게임 내부 렌더링 해상도와는 다릅니다.")
        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
            if (!busy) {
                busy = true
                options = null
                request = null
                error = null
                scope.launch {
                    try {
                        check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
                        val panel = SessionDisplay.panelSize(uiContext)
                        options = withContext(Dispatchers.IO) {
                            DroidDeckSettingsPreview.alternatives(context, panel)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = "설정 조회 실패: 게임 종료 후 설정 상태를 다시 확인해 주세요."
                    } finally {
                        busy = false
                    }
                }
            }
        }) { Text(if (busy) "조회 중" else "변경안 비교") }
        error?.let { Text(it) }
        options?.let { snapshot ->
            Text("조회 당시 설정입니다. 실제 성능 개선이나 게임 실행 성공을 보장하지 않으며 설정을 변경하지 않습니다.")
            snapshot.groupBy { it.scope }.forEach { (scopeName, choices) ->
                val label = when (scopeName) {
                    "steam" -> "Steam 출력"
                    "desktop" -> "데스크톱 출력"
                    else -> "Proton DXVK 브리지 (전체)"
                }
                fun display(value: String): String =
                    if (scopeName == "global") if (value == "true") "사용" else "사용 안 함"
                    else value
                Text("$label: 현재 ${display(choices.first().before)}")
                choices.forEach { change ->
                    val proposed = display(change.proposed)
                    val comparison = change.panel?.let { panel ->
                        OutputResolutionComparison.calculate(panel, change.before, change.proposed)
                    }
                    if (comparison != null) {
                        Text("출력 계산값 (가로 기준): " + comparison.description)
                        if (comparison.currentFollowsWindow)
                            Text("현재 설정은 창 크기를 따라 변경될 수 있습니다.")
                        if (comparison.sameDimensions && change.before != change.proposed)
                            Text("현재 패널의 계산 크기는 같지만 설정 방식은 다릅니다.")
                    }
                    val status = if (DroidDeckSettingsPreview.changed(change)) "변경 후보" else "현재 설정과 동일"
                    if (DroidDeckSettingsPreview.changed(change)) {
                        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
                            try {
                                request = SettingsApplyApproval.prepare(change, SessionDisplay.panelSize(uiContext))
                            } catch (_: Exception) {
                                options = null
                                error = "설정 또는 화면이 변경되었습니다. 다시 비교해 주세요."
                            }
                        }) { Text("$proposed — $status 검토") }
                    } else Text("$proposed — $status")
                }
            }
        }
    }
    request?.let { reviewed ->
        val change = reviewed.change
        val label = when (change.scope) {
            "steam" -> "Steam 출력 해상도"
            "desktop" -> "데스크톱 출력 해상도"
            else -> "Proton DXVK 브리지 (전체)"
        }
        AlertDialog(
            onDismissRequest = { request = null },
            title = { Text("설정 변경 검토") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label)
                    Text("현재 설정: " + change.before)
                    Text("제안 설정: " + change.proposed)
                    Text("기존 저장값: " + (change.snapshot.raw ?: "없음"))
                    change.panel?.let { panel ->
                        val comparison = OutputResolutionComparison.calculate(panel, change.before, change.proposed)
                        if (comparison != null) Text("출력 계산값 (가로 기준): " + comparison.description)
                        Text("창 크기, 화면 방향 또는 게임 내부 설정에 따라 실제 출력과 다를 수 있습니다.")
                    }
                    Text("게임별 설정이 아닙니다. 적용 기능은 검증 전까지 비활성화되어 있습니다.")
                }
            },
            confirmButton = {
                if (gameId != null) TextButton(enabled = !busy, onClick = {
                    val selected = SavedOptimizationChoice.Choice(change.scope, change.proposed)
                    bookmarkTask {
                        withContext(Dispatchers.IO) { SavedOptimizationChoice.save(context, gameId, selected) }
                        bookmark = selected
                        request = null
                        bookmarkMessage = "이 게임의 후보로 저장했습니다. 실제 설정은 변경되지 않았습니다."
                    }
                }) { Text("이 게임의 후보로 저장") }
                else TextButton(onClick = { request = null }) { Text("닫기") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { request = null }) { Text("닫기") } }
        )
    }
}
