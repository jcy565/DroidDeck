package com.droiddeck.launcher.files

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    var batchJob by remember(queue) { mutableStateOf<Job?>(null) }
    var script by remember(queue) { mutableStateOf(ImageTranslationInput.Script.LATIN) }
    var batchReport by remember(queue) { mutableStateOf<ImageBatchOcr.Report?>(null) }
    var exchange by remember(queue) { mutableStateOf<ImageBatchExchange.Request?>(null) }
    var payload by remember(queue) { mutableStateOf("") }
    var reviewed by remember(queue) { mutableStateOf<ImageBatchExchange.Review?>(null) }
    var renderPlan by remember(queue) { mutableStateOf<List<ImageBatchRenderPlan.Entry>>(emptyList()) }
    val exchangeDirectory = File(context.noBackupFilesDir, "image-exchanges")

    fun restoreExchange() {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            try {
                val restored = withContext(Dispatchers.IO) {
                    check(GameplayBudget.allowsOptionalAnalysis(context))
                    ImageExchangeStore.restore(exchangeDirectory, queue, directory) {
                        ensureActive()
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                    }
                }
                if (restored == null) message = "저장된 번역 요청이 없습니다."
                else {
                    exchange = restored
                    payload = ""
                    reviewed = null
                    message = "저장된 번역 요청 복원 완료"
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message = e.message ?: "번역 요청 복원 실패" }
            finally { busy = false }
        }
    }

    fun prepareExchange() {
        if (busy) return
        busy = true
        message = ""
        exchange = null
        payload = ""
        reviewed = null
        scope.launch {
            try {
                exchange = withContext(Dispatchers.IO) {
                    check(GameplayBudget.allowsOptionalAnalysis(context))
                    ImageBatchExchange.prepare(queue, directory) {
                        ensureActive()
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message = e.message ?: "번역 요청 준비 실패" }
            finally { busy = false }
        }
    }

    fun runBatch() {
        if (busy || !ImageOcrRuntime.enabled) return
        busy = true
        message = ""
        batchReport = null
        statuses = emptyMap()
        val selected = script
        batchJob = scope.launch {
            try {
                batchReport = withContext(Dispatchers.IO) {
                    val coroutine = kotlinx.coroutines.currentCoroutineContext()
                    fun checkpoint() {
                        coroutine.ensureActive()
                        if (!GameplayBudget.allowsOptionalAnalysis(context) ||
                            !GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN))
                            throw CancellationException("게임 또는 메모리 상태로 작업을 중단했습니다.")
                    }
                    ImageBatchOcr.run(queue, directory, selected, recognize = { file, language ->
                        ImageOcrRuntime.recognize(file, language, ImageTranslationInput.Rotation.NONE) {
                            checkpoint()
                            true
                        }
                    }, checkpoint = ::checkpoint)
                }
                message = "일괄 OCR 종료. 저장된 초안을 확인하고 번역하세요."
            } catch (e: CancellationException) {
                message = "일괄 OCR 중단. 이미 저장한 초안은 유지됩니다."
                throw e
            } catch (e: Exception) { message = e.message ?: "일괄 OCR 실패" }
            finally { busy = false; batchJob = null }
        }
    }
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
                Text("현재 목록을 기존 저장 목록에 추가할 수 있습니다. 같은 파일은 중복 추가하지 않으며 변경된 원본은 자동 교체하지 않습니다.")
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    message = ""
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                ImageQueueStore.merge(File(context.noBackupFilesDir, "image-worklists"), queue)
                            }
                            message = "이미지 작업 목록 추가 완료. 전체 목록은 폴더 속성에서 복원하세요."
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "작업 목록 저장 실패" }
                        finally { busy = false }
                    }
                }) { Text("현재 작업 목록을 저장 목록에 추가") }
                Text("초안 저장은 번역 완료나 게임 적용을 뜻하지 않습니다.")
                if (ImageOcrRuntime.enabled) {
                    Text("일괄 인식 언어: ${script.label} · 회전 없음")
                    Text("파일을 한 개씩 인식하고 원문과 위치를 앱 내부 초안으로 저장합니다. 기존 초안은 건드리지 않습니다. Google ML Kit의 사용량·성능 통계가 전송될 수 있습니다.")
                    TextButton(enabled = !busy, onClick = {
                        val choices = ImageTranslationInput.Script.values()
                        script = choices[(script.ordinal + 1) % choices.size]
                    }) { Text("인식 언어 변경") }
                    TextButton(enabled = !busy, onClick = { runBatch() }) { Text("미작업 이미지 일괄 OCR 실행") }
                    if (batchJob != null) TextButton(onClick = { batchJob?.cancel() }) { Text("일괄 OCR 중단") }
                    batchReport?.let { report ->
                        Text("신규 초안 ${report.entries.count { it.state == ImageBatchOcr.State.SAVED }}개 · 기존 유지 ${report.entries.count { it.state == ImageBatchOcr.State.EXISTING }}개 · 실패 ${report.entries.count { it.state == ImageBatchOcr.State.FAILED }}개")
                        report.entries.forEach { entry ->
                            val label = when (entry.state) {
                                ImageBatchOcr.State.SAVED -> "인식 초안 저장"
                                ImageBatchOcr.State.EXISTING -> "기존 초안 유지"
                                ImageBatchOcr.State.FAILED -> "인식 또는 저장 실패"
                            }
                            Text("${entry.relative} · $label")
                        }
                    }
                } else Text("현재 빌드에는 실험용 OCR이 포함되지 않았습니다.")
                TextButton(enabled = !busy, onClick = { prepareExchange() }) { Text("이미지 일괄 번역 요청 준비") }
                TextButton(enabled = !busy, onClick = { restoreExchange() }) { Text("저장된 일괄 번역 요청 복원") }
                exchange?.let { request ->
                    Text("${request.targets.size}개 이미지 · ${request.rows.size}개 문장. 다른 이미지 초안은 다음 요청에서 처리하세요.")
                    Text("외부 앱에 전달하는 내용은 OCR 원문과 임시 ID입니다. 민감하거나 공유할 권리가 없는 내용은 보내지 마세요.")
                    Text(request.json)
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        message = ""
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    check(GameplayBudget.allowsOptionalAnalysis(context))
                                    ImageExchangeStore.save(exchangeDirectory, queue, request)
                                }
                                message = "번역 요청 저장 완료. 이전 저장 요청을 교체했습니다."
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { message = e.message ?: "번역 요청 저장 실패" }
                            finally { busy = false }
                        }
                    }) { Text("현재 번역 요청 저장 (이전 요청 교체)") }
                    TextButton(enabled = !busy, onClick = {
                        try {
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, ImageBatchExchange.prompt(request)), "ChatGPT 일괄 번역 요청"))
                        } catch (e: Exception) { message = e.message ?: "공유 실패" }
                    }) { Text("검토한 원문을 외부 앱에 공유") }
                    OutlinedTextField(value = payload, onValueChange = {
                        if (it.length <= ImageBatchExchange.MAX_BYTES) {
                            payload = it
                            reviewed = null
                        }
                    }, enabled = !busy, label = { Text("ChatGPT의 JSON 번역 결과 붙여넣기") }, maxLines = 6)
                    TextButton(enabled = !busy && payload.isNotBlank(), onClick = {
                        reviewed = null
                        message = ""
                        try { reviewed = ImageBatchExchange.review(request, payload) }
                        catch (e: Exception) { message = e.message ?: "번역 결과 검증 실패" }
                    }) { Text("번역 결과 검증 및 미리보기") }
                    reviewed?.let { review ->
                        Text("번역 ${review.translatedRows}개 문장 · ${review.changes.size}개 이미지")
                        review.changes.forEach { change ->
                            Text(change.target.candidate.relative)
                            change.replacement.edits.filterKeys { it !in change.target.draft.edits }.forEach { (id, value) ->
                                val original = change.target.draft.regions?.regions?.firstOrNull { it.id == id }?.text.orEmpty()
                                Text("$original → $value")
                            }
                        }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            message = ""
                            scope.launch {
                                try {
                                    val applied = withContext(Dispatchers.IO) {
                                        ImageBatchExchange.apply(queue, directory, review) {
                                            ensureActive()
                                            check(GameplayBudget.allowsOptionalAnalysis(context))
                                        }
                                    }
                                    message = "초안 저장 ${applied.saved}개 · 실패/변경 ${applied.failed}개"
                                    exchange = null
                                    reviewed = null
                                    payload = ""
                                    statuses = emptyMap()
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { message = e.message ?: "번역 초안 저장 중단. 이미 저장된 초안은 유지됩니다." }
                                finally { busy = false }
                            }
                        }) { Text("검토한 번역을 이미지 초안에 저장") }
                    }
                    TextButton(enabled = !busy, onClick = { exchange = null; reviewed = null; payload = "" }) {
                        Text("일괄 번역 요청 닫기")
                    }
                }
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    message = ""
                    renderPlan = emptyList()
                    scope.launch {
                        try {
                            renderPlan = withContext(Dispatchers.IO) {
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                ImageBatchRenderPlan.inspect(queue, directory) {
                                    ensureActive()
                                    check(GameplayBudget.allowsOptionalAnalysis(context))
                                }
                            }
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "이미지 출력 준비 검사 실패" }
                        finally { busy = false }
                    }
                }) { Text("일괄 이미지 출력 준비 검사") }
                if (renderPlan.isNotEmpty()) {
                    Text("출력 준비 후보 ${renderPlan.count { it.state == ImageBatchRenderPlan.State.READY }} / ${renderPlan.size}개 · 실제 렌더링 검증 전")
                    renderPlan.forEach {
                        val label = when (it.state) {
                            ImageBatchRenderPlan.State.READY -> "영역 준비"
                            ImageBatchRenderPlan.State.MISSING -> "초안 없음"
                            ImageBatchRenderPlan.State.INCOMPLETE -> "번역 또는 인식 미완료"
                            ImageBatchRenderPlan.State.INVALID -> "원본 변경 또는 영역 검증 실패"
                        }
                        Text("${it.candidate.relative} · $label")
                    }
                }
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
