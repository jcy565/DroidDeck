package com.droiddeck.launcher.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    // A different queue owns a different coroutine scope; old navigation and
    // refresh work must be cancelled when the selected worklist changes.
    key(queue) {
        TranslationWorklistContent(queue, onOpen, onDismiss)
    }
}

@Composable
private fun TranslationWorklistContent(queue: TranslationWorklist.Queue, onOpen: (File) -> Unit,
    onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnOpen by rememberUpdatedState(onOpen)
    val drafts = File(context.filesDir, "translation-drafts")
    var report by remember(queue) { mutableStateOf<TranslationWorklist.Report?>(null) }
    var busy by remember(queue) { mutableStateOf(false) }
    var message by remember(queue) { mutableStateOf("") }
    var exportPlan by remember(queue) { mutableStateOf<TranslationWorklistExport.Plan?>(null) }
    var pendingExport by remember(queue) { mutableStateOf<TranslationWorklistExport.Plan?>(null) }

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
        exportPlan = null
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
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        currentOnOpen(selected)
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val plan = pendingExport
        pendingExport = null
        if (uri != null && plan != null) runTask {
            require(uri.scheme == "content")
            withContext(Dispatchers.IO) {
                val coroutine = kotlinx.coroutines.currentCoroutineContext()
                val checkpoint = {
                    coroutine.ensureActive()
                    check(GameplayBudget.allowsOptionalAnalysis(context)) { "게임 상태가 바뀌어 내보내기를 중단했습니다." }
                }
                TranslationWorklistExport.recheck(plan, queue, drafts, checkpoint)
                checkpoint()
                context.contentResolver.openOutputStream(uri, "w")?.use { plan.writeTo(it) }
                    ?: error("ZIP을 저장하지 못했습니다.")
            }
            exportPlan = null
            message = "번역 ZIP 저장 완료. 게임 파일에는 적용하지 않았습니다."
        }
    }

    fun prepareExport() = runTask {
        exportPlan = null
        exportPlan = withContext(Dispatchers.IO) {
            val coroutine = kotlinx.coroutines.currentCoroutineContext()
            TranslationWorklistExport.prepare(queue, drafts) {
                coroutine.ensureActive()
                check(GameplayBudget.allowsOptionalAnalysis(context))
            }
        }
    }

    LaunchedEffect(queue) { refresh() }

    AlertDialog(
        onDismissRequest = { if (!busy && pendingExport == null) onDismiss() },
        title = { Text("한글 패치 작업 목록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("선택한 ${queue.files.size}개 파일 · 목록을 명시적으로 저장하면 다음에 같은 폴더에서 열 수 있습니다. 각 편집기에서 초안은 따로 저장하세요.")
                Text("저장 버튼은 이 폴더의 기존 작업 목록을 교체합니다. 게임 파일과 번역 초안은 변경하지 않습니다.")
                TextButton(enabled = !busy && pendingExport == null, onClick = {
                    runTask {
                        withContext(Dispatchers.IO) {
                            val coroutine = kotlinx.coroutines.currentCoroutineContext()
                            TranslationQueueStore.save(File(context.noBackupFilesDir, "translation-queues"), queue) {
                                coroutine.ensureActive()
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                            }
                        }
                        message = "작업 목록 저장 완료"
                    }
                }) { Text("현재 작업 목록 저장 / 기존 목록 교체") }
                Text("저장된 초안만 집계합니다. 형식 통과는 실제 번역 품질이나 적용 완료를 뜻하지 않습니다.")
                if (busy) Text("파일 및 초안 확인 중…")
                if (message.isNotEmpty()) Text(message)
                TextButton(enabled = !busy && pendingExport == null, onClick = { refresh() }) { Text("진행 상태 새로고침") }
                report?.let { result ->
                    Text("형식 통과 ${result.readyRows} / ${result.totalRows}문장")
                    val ready = result.entries.all { it.state == TranslationWorklist.State.READY_FOR_REVIEW }
                    TextButton(enabled = !busy && pendingExport == null && ready, onClick = { prepareExport() }) {
                        Text("완성된 번역 파일 ZIP 준비")
                    }
                    exportPlan?.let { plan ->
                        Text("내보낼 파일 ${plan.rows.size}개 · ${plan.size}바이트")
                        plan.rows.forEach { row -> Text(row.candidate.relative + " · " + row.bytes + "바이트") }
                        Text("각 파일의 번역 내용을 편집기에서 검토한 뒤 내보내세요. 게임 폴더 밖에 새 ZIP으로 저장하세요. 자동 적용은 지원하지 않습니다.")
                        TextButton(enabled = !busy && pendingExport == null, onClick = {
                            pendingExport = plan
                            try { exporter.launch("droiddeck-translations.zip") }
                            catch (e: Exception) {
                                pendingExport = null
                                message = e.message ?: "파일 선택기를 열지 못했습니다."
                            }
                        }) { Text("검토한 ZIP 저장") }
                        TextButton(enabled = !busy && pendingExport == null, onClick = { exportPlan = null }) {
                            Text("내보내기 취소")
                        }
                    }
                    val next = result.next()
                    TextButton(enabled = !busy && pendingExport == null && next != null, onClick = { next?.let { open(it) } }) {
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
                        TextButton(enabled = !busy && pendingExport == null && entry.state != TranslationWorklist.State.SOURCE_CHANGED &&
                            entry.state != TranslationWorklist.State.DRAFT_UNAVAILABLE,
                            onClick = { open(entry) }) { Text("파일 편집기 열기") }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy && pendingExport == null, onClick = onDismiss) { Text("목록 닫기") } },
    )
}
