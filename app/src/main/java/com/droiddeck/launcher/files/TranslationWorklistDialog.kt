package com.droiddeck.launcher.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun TranslationWorklistDialog(queue: TranslationWorklist.Queue, onOpen: (File) -> Unit,
    onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drafts = File(context.filesDir, "translation-drafts")
    var report by remember(queue) { mutableStateOf<TranslationWorklist.Report?>(null) }
    var busy by remember(queue) { mutableStateOf(false) }
    var message by remember(queue) { mutableStateOf("") }

    fun runTask(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "작업 목록 확인 실패" }
            finally { busy = false }
        }
    }

    fun refresh() = runTask {
        report = null
        report = withContext(Dispatchers.IO) {
            check(GameplayBudget.allowsOptionalAnalysis(context))
            TranslationWorklist.review(queue, drafts) {
                ensureActive()
                check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN)) { "게임 상태가 바뀌어 검토를 중단했습니다." }
            }
        }
    }

    fun open(entry: TranslationWorklist.Entry) = runTask {
        val selected = withContext(Dispatchers.IO) {
            check(GameplayBudget.allowsOptionalAnalysis(context))
            val file = TranslationDiscovery.open(queue.root, entry.candidate)
            check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN))
            file
        }
        onOpen(selected)
    }

    LaunchedEffect(queue) { refresh() }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("한글 패치 작업 목록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("선택한 ${queue.files.size}개 파일 · 이 화면을 닫으면 목록은 사라집니다. 각 편집기에서 초안을 저장하고 돌아오세요.")
                Text("저장된 초안만 집계합니다. 형식 통과는 실제 번역 품질이나 적용 완료를 뜻하지 않습니다.")
                if (busy) Text("파일 및 초안 확인 중…")
                if (message.isNotEmpty()) Text(message)
                TextButton(enabled = !busy, onClick = { refresh() }) { Text("진행 상태 새로고침") }
                report?.let { result ->
                    Text("형식 통과 ${result.readyRows} / ${result.totalRows}문장")
                    val next = result.next()
                    TextButton(enabled = !busy && next != null, onClick = { next?.let { open(it) } }) {
                        Text("다음 미완료 파일 열기")
                    }
                    result.entries.forEach { entry ->
                        val label = when (entry.state) {
                            TranslationWorklist.State.NOT_STARTED -> "미시작"
                            TranslationWorklist.State.IN_PROGRESS -> "진행 중"
                            TranslationWorklist.State.READY_FOR_REVIEW -> "검토 가능"
                            TranslationWorklist.State.SOURCE_CHANGED -> "원본 변경 또는 읽기 실패 · 다시 탐색"
                            TranslationWorklist.State.DRAFT_UNAVAILABLE -> "초안 읽기 실패 · 기존 초안 보존"
                        }
                        Text("${entry.candidate.relative} · $label · ${entry.ready}/${entry.candidate.rows}")
                        TextButton(enabled = !busy && entry.state != TranslationWorklist.State.SOURCE_CHANGED &&
                            entry.state != TranslationWorklist.State.DRAFT_UNAVAILABLE,
                            onClick = { open(entry) }) { Text("파일 편집기 열기") }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("목록 닫기") } },
    )
}
