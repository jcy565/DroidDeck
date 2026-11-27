package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RecoveryAction(val button: String, val success: String) {
    RESTORE("복원하기", "이전 설정을 복원했습니다."),
    ARCHIVE("현재 값 유지하고 보관", "현재 설정을 유지하고 백업을 보관했습니다."),
}

private data class Confirmation(val review: SettingsBackup.RecoveryReview, val action: RecoveryAction)

@Composable
internal fun SettingsRecoveryReviewPanel() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var entries by remember(context) { mutableStateOf<List<SettingsBackup.ReviewEntry>?>(null) }
    var busy by remember(context) { mutableStateOf(false) }
    var message by remember(context) { mutableStateOf<String?>(null) }
    var pending by remember(context) { mutableStateOf<Confirmation?>(null) }

    var editing by remember(context) { mutableStateOf<String?>(null) }

    fun refresh(confirmed: Confirmation? = null, notice: String? = null) {
        if (busy) return
        busy = true
        pending = null
        entries = null
        message = notice
        scope.launch {
            try {
                if (confirmed != null) {
                    withContext(Dispatchers.IO) {
                        when (confirmed.action) {
                            RecoveryAction.RESTORE -> SettingsBackup.restoreReviewed(context, confirmed.review, approved = true)
                            RecoveryAction.ARCHIVE -> SettingsBackup.keepCurrentAndArchive(context, confirmed.review, approved = true)
                        }
                    }
                    message = confirmed.action.success
                }
                entries = withContext(Dispatchers.IO) {
                    check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
                    SettingsBackup.reviewAll(context)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                message = when {
                    message != null -> "$message 목록을 다시 조회해 주세요."
                    confirmed?.action == RecoveryAction.ARCHIVE -> "보관 결과를 확인할 수 없습니다. 현재 설정과 복구 기록을 다시 확인해 주세요."
                    confirmed != null -> "복원 결과를 확인할 수 없습니다. 게임을 종료하고 현재 설정과 복구 기록을 다시 확인해 주세요."
                    else -> "복구 기록을 읽지 못했습니다. 게임을 종료한 후 다시 확인해 주세요."
                }
            } finally {
                busy = false
            }
        }
    }

    editing?.let { key ->
        SettingsChangeDialog(key, settingLabel(key), onDismiss = { editing = null }) { notice ->
            editing = null
            refresh(notice = notice)
        }
    }

    pending?.let { confirmation ->
        val review = confirmation.review
        val label = settingLabel(review.key)
        val restoring = confirmation.action == RecoveryAction.RESTORE
        val details = if (restoring) {
            val before = review.before?.let { "복원할 값: $it" }
                ?: "이 항목에 저장된 값을 삭제해 이전 상태로 되돌립니다."
            "$before\n복원이 끝나면 이 백업은 삭제됩니다."
        } else {
            "이전 값: ${review.before ?: "저장된 값 없음"}\n현재 설정을 유지합니다. 이 백업은 조회 전용 기록으로 보관되며 이 항목의 보관 공간 한 칸을 사용합니다."
        }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (restoring) "$label 복원" else "$label 백업 보관") },
            text = { Text("현재 값: ${review.current ?: "저장된 값 없음"}\n$details") },
            confirmButton = {
                TextButton(onClick = { if (pending == confirmation) refresh(confirmation) }) {
                    Text(confirmation.action.button)
                }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("취소") } },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("설정 변경 및 복구")
        Text("Steam·데스크톱 출력 해상도 및 Proton DXVK 설정은 선택한 게임에만 적용되는 값이 아닙니다.")
        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { refresh() }) {
            Text(if (busy) "처리 중" else "설정 및 백업 조회")
        }
        message?.let { Text(it) }
        entries?.let { snapshot ->
            Text("조회 시점의 기록입니다.")
            snapshot.forEach { entry ->
                val label = settingLabel(entry.key)
                val status = when (entry.status) {
                    SettingsBackup.ReviewStatus.NO_BACKUP -> "백업 없음"
                    SettingsBackup.ReviewStatus.INVALID_BACKUP -> "손상되거나 불완전한 기록"
                    SettingsBackup.ReviewStatus.CONFLICT -> "현재 값과 충돌: 복구 보류"
                    SettingsBackup.ReviewStatus.LEGACY_BACKUP -> "이전 형식 백업: 조회만 가능"
                    SettingsBackup.ReviewStatus.READY -> "복구 검토 가능"
                }
                Text("$label: $status")
                if (entry.status == SettingsBackup.ReviewStatus.NO_BACKUP) {
                    TextButton(onClick = { editing = entry.key }) { Text("$label 변경") }
                }
                fun display(value: String?): String = value?.take(100) ?: "저장된 값 없음"
                entry.review?.let { review ->
                    Text("이전: ${display(review.before)} / 적용: ${display(review.applied)} / 현재: ${display(review.current)}")
                    if (entry.status == SettingsBackup.ReviewStatus.READY) {
                        TextButton(onClick = { pending = Confirmation(review, RecoveryAction.RESTORE) }) {
                            Text("$label 복원 검토")
                        }
                    } else if (entry.status == SettingsBackup.ReviewStatus.CONFLICT &&
                        review.recordId != null && !entry.archiveOccupied) {
                        TextButton(onClick = { pending = Confirmation(review, RecoveryAction.ARCHIVE) }) {
                            Text("$label 백업 보관 검토")
                        }
                    }
                }
                if (entry.archiveOccupied) {
                    Text("이전 기록 보관됨: 덮어쓰기 금지")
                    val archived = entry.archived
                    if (archived == null) Text("보관 기록의 형식이 올바르지 않습니다. 원본은 유지합니다.")
                    else Text("보관 당시 이전: ${display(archived.before)} / 적용: ${display(archived.applied)} / 현재: ${display(archived.current)}")
                }
            }
        }
    }
}

private fun settingLabel(key: String): String = when (key) {
    "displayResolution.steam" -> "Steam 출력 해상도"
    "displayResolution.desktop" -> "데스크톱 출력 해상도"
    "bridgeProtonDxvk" -> "Proton DXVK 브리지"
    else -> key
}
