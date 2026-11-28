package com.droiddeck.launcher.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Explicit, folder-scoped entry point to the existing AI workflows. */
@Composable
internal fun AiWorkspaceDialog(folder: File, listed: List<File>, onResumeFile: (File) -> Unit,
    onFolderTools: () -> Unit,
    onTextQueue: (TranslationWorklist.Queue) -> Unit,
    onImageQueue: (ImageAssetDiscovery.Queue) -> Unit, onDismiss: () -> Unit) {
    key(folder) {
        val context = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        val host = remember { AiHostBridge.availability() }
        var busy by remember { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        var admission by remember { mutableStateOf<GameplayBudget.Admission?>(null) }
        var exchangeStatus by remember { mutableStateOf<SharedAgentReply.Status?>(null) }
        var choosingResumeFile by remember { mutableStateOf(false) }
        var resumeFilter by remember { mutableStateOf("") }
        val inboxRevision by SharedAgentReply.revision.collectAsState()
        LaunchedEffect(inboxRevision) {
            // A previously inspected status is a snapshot; do not imply it is live.
            exchangeStatus = null
            choosingResumeFile = false
        }
        fun runTask(action: suspend () -> Unit) {
            if (busy) return
            busy = true
            message = ""
            scope.launch {
                try { action() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = e.message ?: "AI 작업 목록을 열지 못했습니다." }
                finally { busy = false }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!busy) onDismiss() },
            title = { Text("AI 작업 센터") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text("현재 폴더: " + folder.name.ifBlank { folder.path })
                    Text("AI 연결 모듈: " + (host.version ?: "미확인"))
                    host.reason?.let { Text(it) }
                    Text("ChatGPT 연동: " + if (host.enabled) "수동 요청·응답 교환 지원" else "사용 불가")
                    Text("로컬 모델: " + if (host.enabled && LocalAgentRuntime.enabled)
                        "실험용 런타임 포함 · 모델 별도 필요" else "이 빌드에서 사용 불가")
                    Text("이미지 OCR: " + if (ImageOcrRuntime.enabled) "런타임 포함" else "수동 영역 입력")
                    Text("서명 데이터 업데이트: " + if (host.enabled && AiGlossaryUpdates.configured)
                        "설정됨" else "미설정 또는 비호환")
                    TextButton(enabled = !busy, onClick = {
                        runTask {
                            admission = withContext(Dispatchers.IO) { GameplayBudget.admission(context) }
                        }
                    }) { Text("집중 보호 및 메모리 상태 확인") }
                    admission?.let { Text("조회 시점: " + it.guidance) }
                    TextButton(enabled = !busy && host.enabled, onClick = {
                        runTask {
                            exchangeStatus = null
                            exchangeStatus = withContext(Dispatchers.IO) { SharedAgentReply.status(context) }
                        }
                    }) { Text("저장된 ChatGPT 작업 상태 확인") }
                    exchangeStatus?.let {
                        Text("기기 전체 · 조회 시점: " + it.label)
                        Text("요청을 만든 원래 파일에서 재개하세요. 현재 폴더의 작업이라고 단정할 수 없습니다.")
                        if (it != SharedAgentReply.Status.NO_REQUEST) {
                            TextButton(enabled = !busy, onClick = {
                                choosingResumeFile = !choosingResumeFile
                            }) { Text("현재 폴더에서 원래 파일 선택 및 재개") }
                        }
                    }
                    if (choosingResumeFile) {
                        Text("파일명을 고르면 기존 요청과 원본·초안의 일치 여부를 편집기에서 확인합니다.")
                        OutlinedTextField(resumeFilter, { resumeFilter = it.take(256) },
                            enabled = !busy, singleLine = true, label = { Text("파일명 검색") })
                        val page = SavedExchangeFiles.candidates(folder, listed, resumeFilter)
                        if (page.files.isEmpty()) Text("표시할 지원 파일이 없습니다.")
                        if (page.more) Text("30개까지만 표시합니다. 파일명 검색으로 범위를 좁히세요.")
                        page.files.forEach { candidate ->
                            TextButton(enabled = !busy, onClick = {
                                runTask {
                                    val selected = withContext(Dispatchers.IO) {
                                        check(GameplayBudget.allowsOptionalAnalysis(context))
                                        SavedExchangeFiles.selected(folder, candidate)
                                    }
                                    onResumeFile(selected)
                                }
                            }) { Text(candidate.name) }
                        }
                    }
                    Text("저장한 작업 목록을 현재 폴더에서 다시 엽니다. 자동 탐색·전송·패치 적용은 하지 않습니다.")
                    TextButton(enabled = !busy, onClick = {
                        runTask {
                            val queue = withContext(Dispatchers.IO) {
                                val coroutine = currentCoroutineContext()
                                TranslationQueueStore.load(File(context.noBackupFilesDir, "translation-queues"), folder) {
                                    coroutine.ensureActive()
                                    check(GameplayBudget.allowsOptionalAnalysis(context))
                                }
                            }
                            if (queue == null) message = "이 폴더에 저장된 텍스트 작업 목록이 없습니다."
                            else onTextQueue(queue)
                        }
                    }) { Text("텍스트 한글 패치 이어서 작업") }
                    TextButton(enabled = !busy, onClick = {
                        runTask {
                            val queue = withContext(Dispatchers.IO) {
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                ImageQueueStore.load(File(context.noBackupFilesDir, "image-worklists"), folder).also {
                                    currentCoroutineContext().ensureActive()
                                    check(GameplayBudget.allowsOptionalAnalysis(context))
                                }
                            }
                            if (queue == null) message = "이 폴더에 저장된 이미지 작업 목록이 없습니다."
                            else onImageQueue(queue)
                        }
                    }) { Text("이미지 한글화 이어서 작업") }
                    TextButton(enabled = !busy, onClick = onFolderTools) { Text("현재 폴더의 번역 대상 탐색 도구 열기") }
                    if (busy) Text("확인 중…")
                    if (message.isNotBlank()) Text(message)
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } }
        )
    }
}
