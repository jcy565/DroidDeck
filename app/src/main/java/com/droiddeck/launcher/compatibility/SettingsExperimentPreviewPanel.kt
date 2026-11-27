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
internal fun SettingsExperimentPreviewPanel() {
    val uiContext = LocalContext.current
    val context = uiContext.applicationContext
    val scope = rememberCoroutineScope()
    var options by remember(context) { mutableStateOf<List<DroidDeckSettingsPreview.Change>?>(null) }
    var request by remember(context) { mutableStateOf<SettingsApplyApproval.Request?>(null) }
    var busy by remember(context) { mutableStateOf(false) }
    var error by remember(context) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("출력 해상도·DXVK 설정 미리보기")
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
                    Text("게임별 설정이 아닙니다. 적용 기능은 검증 전까지 비활성화되어 있습니다.")
                }
            },
            confirmButton = { TextButton(onClick = { request = null }) { Text("닫기") } }
        )
    }
}
