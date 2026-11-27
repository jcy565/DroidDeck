package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.session.SessionDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsChangeDialog(key: String, label: String, onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bridge = key == "bridgeProtonDxvk"
    var preview by remember(key) { mutableStateOf<DroidDeckSettingsPreview.Change?>(null) }
    var busy by remember(key) { mutableStateOf(false) }

    fun display(value: String) = when {
        !bridge -> value
        value == "true" -> "켜기"
        else -> "끄기"
    }

    fun select(value: String) {
        if (busy) return
        busy = true
        preview = null
        scope.launch {
            try {
                preview = withContext(Dispatchers.IO) {
                    check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
                    if (bridge) DroidDeckSettingsPreview.protonDxvkBridge(context, value.toBooleanStrict())
                    else DroidDeckSettingsPreview.outputResolution(context, key.substringAfter('.'),
                        SessionDisplay.panelSize(context), value)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult("미리보기를 읽지 못했습니다. 게임을 종료한 후 다시 확인해 주세요.")
            } finally {
                busy = false
            }
        }
    }

    fun apply(change: DroidDeckSettingsPreview.Change) {
        if (busy || preview != change) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val panel = if (bridge) null else SessionDisplay.panelSize(context)
                    DroidDeckSettingsTransaction.apply(context, change, panel, approved = true)
                }
                onResult("$label 설정을 백업하고 변경했습니다.")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                onResult("적용 결과를 확인하려면 현재 설정과 백업을 다시 조회해 주세요.")
            } finally {
                busy = false
            }
        }
    }

    val change = preview
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("$label 변경") },
        text = {
            Column {
                Text(when (key) {
                    "displayResolution.steam" -> "Steam 모드 전체에 적용됩니다."
                    "displayResolution.desktop" -> "데스크톱 모드 전체에 적용됩니다."
                    else -> "앱 전체의 Proton DXVK 브리지 설정에 적용됩니다."
                })
                Row {
                    val options = if (bridge) listOf("true", "false") else listOf("1280x720", "1600x900")
                    options.forEach { value ->
                        TextButton(enabled = !busy, onClick = { select(value) }) { Text(display(value)) }
                    }
                }
                if (change != null) {
                    Text("현재 값: ${display(change.before)}")
                    Text("선택한 값: ${display(change.proposed)}")
                    if (!DroidDeckSettingsPreview.changed(change)) Text("이미 사용 중인 값입니다.")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && change != null && DroidDeckSettingsPreview.changed(change),
                onClick = { if (change != null) apply(change) }) { Text("백업 후 적용") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("취소") } },
    )
}
