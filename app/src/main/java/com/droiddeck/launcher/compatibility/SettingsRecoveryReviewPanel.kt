package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
