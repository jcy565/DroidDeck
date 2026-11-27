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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ImageAssetWorklistDialog(queue: ImageAssetDiscovery.Queue, onOpen: (File, ImageDraftStore.Identity) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val directory = File(context.noBackupFilesDir, "image-translation-drafts")
    var busy by remember(queue) { mutableStateOf(false) }
    var message by remember(queue) { mutableStateOf("") }
    var statuses by remember(queue) { mutableStateOf<Map<String, String>>(emptyMap()) }
    fun refresh() {
        if (busy) return
        busy = true
        statuses = emptyMap()
        message = ""
        scope.launch {
            try {
                statuses = withContext(Dispatchers.IO) {
                    check(GameplayBudget.allowsOptionalAnalysis(context))
                    queue.candidates.associate { candidate ->
                        ensureActive()
                        check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN))
                        val file = try { ImageAssetDiscovery.open(queue.root, candidate) }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { null }
                        val status = if (file == null) "원본 변경 또는 읽기 실패"
                        else try {
                            val identity = ImageDraftStore.Identity(file.canonicalPath, candidate.digest)
                            val draft = ImageDraftStore.load(directory, identity)
                            if (draft == null) "저장 초안 없음"
                            else "초안 저장됨 · 영역 번역 ${draft.edits.size}개"
                        } catch (e: CancellationException) { throw e }
                          catch (_: Exception) { "초안 확인 실패" }
                        candidate.relative to status
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message = e.message ?: "작업 목록 확인 실패" }
            finally { busy = false }
        }
    }
    fun open(candidate: ImageAssetDiscovery.Candidate) {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    check(GameplayBudget.allowsOptionalAnalysis(context))
                    ImageAssetDiscovery.open(queue.root, candidate).also {
                        ensureActive()
                        check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN))
                    }
                }
                onOpen(file, ImageDraftStore.Identity(file.canonicalPath, candidate.digest))
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message = e.message ?: "파일 열기 실패" }
            finally { busy = false }
        }
    }
    LaunchedEffect(queue) { refresh() }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("이미지 한글화 작업 목록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("선택한 ${queue.candidates.size}개 파일 · 각 편집기에서 초안을 직접 저장하세요.")
                Text("목록 저장은 같은 폴더의 이전 작업 목록을 교체합니다. 저장한 목록은 폴더 속성에서 복원할 수 있습니다.")
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    message = ""
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                ImageQueueStore.save(File(context.noBackupFilesDir, "image-worklists"), queue)
                            }
                            message = "이미지 작업 목록 저장 완료"
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "작업 목록 저장 실패" }
                        finally { busy = false }
                    }
                }) { Text("현재 작업 목록 저장 (기존 목록 교체)") }
                Text("초안 저장은 번역 완료나 게임 적용을 뜻하지 않습니다.")
                if (busy) Text("파일 확인 중…")
                if (message.isNotEmpty()) Text(message)
                TextButton(enabled = !busy, onClick = { refresh() }) { Text("진행 상태 새로고침") }
                val next = queue.candidates.firstOrNull { statuses[it.relative] == "저장 초안 없음" }
                TextButton(enabled = !busy && next != null, onClick = { next?.let { open(it) } }) {
                    Text("다음 미시작 이미지 열기")
                }
                queue.candidates.forEach { candidate ->
                    Text("${candidate.relative} · ${candidate.width} × ${candidate.height}px")
                    Text(statuses[candidate.relative] ?: "미확인")
                    TextButton(enabled = !busy && statuses[candidate.relative] != "원본 변경 또는 읽기 실패",
                        onClick = { open(candidate) }) { Text("이미지 편집기 열기") }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("목록 닫기") } },
    )
}
