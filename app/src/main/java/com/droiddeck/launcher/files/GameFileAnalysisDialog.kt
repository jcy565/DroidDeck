package com.droiddeck.launcher.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Analysis of the explicitly selected saved file; never modifies editor state. */
@Composable
internal fun GameFileAnalysisDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var busy by remember(file) { mutableStateOf(false) }
    var result by remember(file) { mutableStateOf<GameFileAnalysis.Result?>(null) }
    var message by remember(file) { mutableStateOf<String?>(null) }

    fun analyze(mode: GameFileAnalysis.Mode) {
        if (busy) return
        busy = true
        result = null
        message = null
        scope.launch {
            try {
                result = withContext(Dispatchers.IO) {
                    val checkpoint = {
                        ensureActive()
                        if (!GameplayBudget.allowsOptionalAnalysis(context)) throw AnalysisDeferred()
                    }
                    GameFileAnalysis.analyze(file, mode, checkpoint)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: AnalysisDeferred) {
                message = "게임 또는 메모리 상태 때문에 분석을 보류했습니다."
            } catch (_: Exception) {
                message = "지원하지 않는 설정, 인코딩 또는 파일 상태입니다. 원본 파일은 변경하지 않았습니다."
            } finally {
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 파일 분석") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(file.name)
                Text("저장된 UTF-8 파일을 기기에서만 분석합니다. 수정 및 전송은 하지 않습니다.")
                val choices = if (file.extension.equals("log", true))
                    listOf(GameFileAnalysis.Mode.LOG to "오류 로그 진단")
                else listOf(
                    GameFileAnalysis.Mode.RESOLUTION_720 to "720p 최적화 미리보기",
                    GameFileAnalysis.Mode.RESOLUTION_900 to "900p 최적화 미리보기",
                    GameFileAnalysis.Mode.FRAME_LIMIT_30 to "30 FPS 제한 미리보기",
                )
                choices.forEach { (mode, label) ->
                    Button(
                        onClick = { analyze(mode) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
                if (busy) Text("분석 중")
                message?.let { Text(it) }
                result?.let { report ->
                    report.diagnosis?.let { Text(it) }
                    if (report.diagnosis == null) {
                        if (report.changes.isEmpty()) Text("변경할 항목이 없습니다.")
                        report.changes.forEach { change ->
                            Text(change.key + ": " + change.before + " → " + change.after)
                        }
                        Text("미리보기 결과입니다. 원본 파일은 변경하지 않았습니다.")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (busy) "분석 취소" else "닫기") } },
    )
}

private class AnalysisDeferred : IllegalStateException()
