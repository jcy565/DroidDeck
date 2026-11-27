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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read-only, explicitly requested review. No polling or settings writes. */
@Composable
internal fun SettingsRecoveryReviewPanel() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var entries by remember(context) { mutableStateOf<List<SettingsBackup.ReviewEntry>?>(null) }
    var selected by remember(context) { mutableStateOf<SettingsRecoveryApproval.Request?>(null) }
    var busy by remember(context) { mutableStateOf(false) }
    var error by remember(context) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("설정 복구 기록")
        Text("Steam·데스크톱 출력 해상도 및 Proton DXVK 설정은 선택한 게임에만 적용되는 값이 아닙니다.")
        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
            if (!busy) {
                busy = true
                error = null
                entries = null
                selected = null
                scope.launch {
                    try {
                        entries = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
                            SettingsBackup.reviewAll(context)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = "복구 기록을 읽지 못했습니다. 게임을 종료한 후 다시 확인해 주세요."
                    } finally {
                        busy = false
                    }
                }
            }
        }) { Text(if (busy) "조회 중" else "복구 기록 확인") }
        error?.let { Text(it) }
        entries?.let { snapshot ->
            Text("조회 시점의 기록입니다. 자동 적용 및 복구는 실행되지 않습니다.")
            snapshot.forEach { entry ->
                val label = when (entry.key) {
                    "displayResolution.steam" -> "Steam 출력 해상도"
                    "displayResolution.desktop" -> "데스크톱 출력 해상도"
                    "bridgeProtonDxvk" -> "Proton DXVK 브리지"
                    else -> entry.key
                }
                val status = when (entry.status) {
                    SettingsBackup.ReviewStatus.NO_BACKUP -> "백업 없음"
                    SettingsBackup.ReviewStatus.INVALID_BACKUP -> "손상되거나 불완전한 기록"
                    SettingsBackup.ReviewStatus.CONFLICT -> "현재 값과 충돌: 복구 보류"
                    SettingsBackup.ReviewStatus.LEGACY_BACKUP -> "이전 형식 백업: 조회만 가능"
                    SettingsBackup.ReviewStatus.READY -> "복구 검토 가능"
                }
                Text("$label: $status")
                fun display(value: String?): String = value?.take(100) ?: "저장된 값 없음"
                entry.review?.let { review ->
                    Text("이전: ${display(review.before)} / 적용: ${display(review.applied)} / 현재: ${display(review.current)}")
                }
                val actions = entry.availableActions
                actions.forEach { action ->
                    val actionLabel = when (action) {
                        SettingsBackup.ReviewAction.RESTORE -> "이전 저장값 복구 검토"
                        SettingsBackup.ReviewAction.KEEP_CURRENT_AND_ARCHIVE -> "현재 설정 유지 및 백업 보관 검토"
                    }
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
                        selected = SettingsRecoveryApproval.prepare(entry, action)
                    }) { Text(actionLabel) }
                }
                if (actions.isNotEmpty())
                    Text("검증 완료 전에는 실제 복구 및 보관을 실행할 수 없습니다.")
                if (entry.archiveOccupied) {
                    Text("이전 기록 보관됨: 덮어쓰기 금지")
                    val archived = entry.archived
                    if (archived == null) Text("보관 기록이 이전 형식이거나 불완전하여 정확한 값을 확인할 수 없습니다. 원본은 유지합니다.")
                    else Text("보관 당시 이전: ${display(archived.before)} / 적용: ${display(archived.applied)} / 현재: ${display(archived.current)}")
                }
            }
        }
    }
    selected?.let { request ->
        val review = request.reviewed
        val label = when (review.key) {
            "displayResolution.steam" -> "Steam 출력 해상도"
            "displayResolution.desktop" -> "데스크톱 출력 해상도"
            else -> "Proton DXVK 브리지 (전체)"
        }
        val action = when (request.action) {
            SettingsBackup.ReviewAction.RESTORE -> "이전 저장값 복구"
            SettingsBackup.ReviewAction.KEEP_CURRENT_AND_ARCHIVE -> "현재 설정 유지 및 백업 보관"
        }
        fun display(value: String?): String = value?.take(100) ?: "저장된 값 없음"
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(action + " 검토") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(label)
                    Text("현재 저장값: " + display(review.current))
                    Text("작업 후 저장값: " + display(request.targetRaw))
                    if (request.targetRaw == null)
                        Text("저장값 없음은 기본값 또는 이전 설정 규칙 사용을 의미합니다.")
                    Text("조회 시점의 기록이며 실제 적용은 아직 비활성화되어 있습니다.")
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("닫기") } }
        )
    }
}
