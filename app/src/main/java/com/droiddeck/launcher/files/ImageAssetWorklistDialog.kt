package com.droiddeck.launcher.files

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var staged by remember(queue) { mutableStateOf<ImageBatchRender.Report?>(null) }
    var stagedPreview by remember(queue) { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var renderJob by remember(queue) { mutableStateOf<Job?>(null) }
    var batchStyle by remember(queue) { mutableStateOf(ImageOverlayPlan.Background.MATCH_FLAT) }
    val approvals = File(context.noBackupFilesDir, "image-approvals")
    var approvedStates by remember(queue) { mutableStateOf<Map<String, ImageApprovalStore.State>>(emptyMap()) }
    var approvedPreview by remember(queue) { mutableStateOf<Pair<String, Bitmap>?>(null) }
    var patch by remember(queue) { mutableStateOf<ImagePatchArchive.Package?>(null) }
    var exportPending by remember(queue) { mutableStateOf(false) }
    val exportPatch = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        exportPending = false
        val saved = patch
        if (uri != null && saved != null && !busy) {
            busy = true
            message = ""
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                        fun checkWork() {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                        }
                        require(ImagePatchArchive.verify(saved.file, ::checkWork) == saved.rows &&
                            ImagePatchArchive.digest(saved.file, ::checkWork) == saved.digest)
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                        var total = 0L
                        java.nio.file.Files.newInputStream(saved.file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS).use { input ->
                            requireNotNull(context.contentResolver.openOutputStream(uri, "w")).use { output ->
                                val buffer = ByteArray(65536)
                                while (true) {
                                    ensureActive()
                                    checkWork()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    require(total <= ImagePatchArchive.MAX_ARCHIVE)
                                    output.write(buffer, 0, n)
                                    digest.update(buffer, 0, n)
                                }
                            }
                        }
                        require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == saved.digest)
                    }
                    message = "ZIP 내보내기 완료. 게임에는 적용하지 않았습니다."
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { message = "ZIP 내보내기 실패. 대상에 불완전한 파일이 남았을 수 있습니다." }
                finally { busy = false }
            }
        }
    }

    fun approvalTask(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "이미지 승인 작업 실패" }
            finally { busy = false }
        }
    }

    fun stageImages() {
        if (busy || staged != null) return
        busy = true
        message = ""
        val style = ImageOverlayPlan.Appearance(batchStyle, ImageOverlayPlan.Foreground.AUTO, ImageOverlayPlan.Weight.NORMAL)
        renderJob = scope.launch {
            try {
                staged = withContext(Dispatchers.IO) {
                    val coroutine = kotlinx.coroutines.currentCoroutineContext()
                    fun checkpoint() {
                        coroutine.ensureActive()
                        if (!GameplayBudget.allowsOptionalAnalysis(context) ||
                            !GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN))
                            throw CancellationException("게임 또는 메모리 상태로 출력 중단")
                    }
                    ImageBatchRender.run(queue, directory, File(context.cacheDir, "image-batch-review"),
                        encode = { file, id, draft, ext, output, check ->
                            ImageBatchRenderRuntime.encode(file, id, draft, ext, output, style, check)
                        }, verify = ImageBatchRenderRuntime::verify, checkpoint = ::checkpoint)
                }
                message = "검토용 출력 생성 완료. 아직 게임에 적용하지 않았습니다."
            } catch (e: CancellationException) {
                message = "일괄 출력 중단. 이번 작업의 임시 출력은 제거됩니다."
                throw e
            } catch (e: Exception) { message = e.message ?: "일괄 출력 실패" }
            finally { busy = false; renderJob = null }
        }
    }

    fun discardStaged() {
        if (busy) return
        val report = staged ?: return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { ImageBatchRender.discard(report) }
                staged = null
                stagedPreview = null
                message = "임시 검토 파일 제거 완료"
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { message = e.message ?: "검토 파일 제거 실패" }
            finally { busy = false }
        }
    }

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
        onDismissRequest = { if (!busy && !exportPending) onDismiss() },
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
                Text("일괄 출력은 최대 파일당 8MiB, 실행당 64MiB입니다. TGA는 비압축, DDS는 손실 재압축, JPEG·WebP는 PNG로 출력합니다. 모든 결과는 앱 캐시의 미승인 검토본입니다.")
                TextButton(enabled = !busy && staged == null, onClick = {
                    val choices = ImageOverlayPlan.Background.values()
                    batchStyle = choices[(batchStyle.ordinal + 1) % choices.size]
                }) { Text("일괄 배경 방식: ${batchStyle.label}") }
                TextButton(enabled = !busy && staged == null, onClick = { stageImages() }) { Text("검토용 이미지 일괄 생성") }
                if (renderJob != null) TextButton(onClick = { renderJob?.cancel() }) { Text("일괄 출력 중단") }
                staged?.let { report ->
                    Text("생성 ${report.entries.count { it.state == ImageBatchRender.State.STAGED }} / ${report.entries.size}개")
                    report.entries.forEach { entry ->
                        val label = when (entry.state) {
                            ImageBatchRender.State.STAGED -> "미승인 검토본"
                            ImageBatchRender.State.SKIPPED -> "초안 미완료"
                            ImageBatchRender.State.FAILED -> "출력 실패"
                            ImageBatchRender.State.LIMIT -> "용량 한도"
                        }
                        Text("${entry.relative} · $label")
                        entry.artifact?.let { artifact ->
                            TextButton(enabled = !busy, onClick = {
                                busy = true
                                message = ""
                                stagedPreview = null
                                scope.launch {
                                    try {
                                        val image = withContext(Dispatchers.IO) {
                                            ImageBatchRenderRuntime.preview(artifact) {
                                                ensureActive()
                                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                            }
                                        }
                                        stagedPreview = entry.relative to image
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { message = e.message ?: "출력 검토 실패" }
                                    finally { busy = false }
                                }
                            }) { Text("실제 출력 미리보기 (${artifact.file.extension.uppercase()})") }
                            val sameFormat = File(artifact.candidate.relative).extension.lowercase() == artifact.file.extension
                            if (sameFormat) TextButton(enabled = !busy && stagedPreview?.first == entry.relative, onClick = {
                                approvalTask {
                                    withContext(Dispatchers.IO) {
                                        check(GameplayBudget.allowsOptionalAnalysis(context))
                                        ImageApprovalStore.approve(approvals, queue, directory, artifact,
                                            ImageBatchRenderRuntime::verify) {
                                            ensureActive()
                                            check(GameplayBudget.allowsOptionalAnalysis(context))
                                        }
                                    }
                                    approvedStates = approvedStates + (entry.relative to ImageApprovalStore.State.VALID)
                                    message = "검토한 이미지 승인 및 저장 완료. 게임에는 적용하지 않았습니다."
                                }
                            }) { Text("미리본 이미지 승인 (기존 승인 교체)") }
                            else Text("원본과 출력 형식이 달라 패치 후보로 승인할 수 없습니다.")
                        }
                    }
                    stagedPreview?.let { (name, image) ->
                        Text("$name · 축소 검토")
                        Image(bitmap = remember(image) { image.asImageBitmap() }, contentDescription = name,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
                    }
                    TextButton(enabled = !busy, onClick = { discardStaged() }) { Text("임시 검토본 삭제") }
                    Text("작업 목록을 닫으면 검토 목록은 사라집니다. 캐시는 기기에서 제거될 수 있습니다.")
                }
                Text("승인본은 원본·초안·출력 데이터가 일치할 때만 유효합니다. 승인 자체는 게임 적용이 아닙니다.")
                TextButton(enabled = !busy, onClick = {
                    approvalTask {
                        approvedPreview = null
                        approvedStates = withContext(Dispatchers.IO) {
                            queue.candidates.associate { c ->
                                ensureActive()
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                val state = try {
                                    if (ImageApprovalStore.read(approvals, queue, directory, c,
                                        ImageBatchRenderRuntime::verify) == null) ImageApprovalStore.State.MISSING
                                    else ImageApprovalStore.State.VALID
                                } catch (e: CancellationException) { throw e }
                                  catch (_: Exception) { ImageApprovalStore.State.INVALID }
                                c.relative to state
                            }
                        }
                    }
                }) { Text("저장된 이미지 승인 목록 확인") }
                queue.candidates.forEach { c ->
                    approvedStates[c.relative]?.let { state ->
                        val label = when (state) {
                            ImageApprovalStore.State.MISSING -> "승인 없음"
                            ImageApprovalStore.State.VALID -> "승인 유효"
                            ImageApprovalStore.State.INVALID -> "승인 무효 또는 읽기 실패"
                        }
                        Text("${c.relative} · $label")
                        if (state == ImageApprovalStore.State.VALID) {
                            TextButton(enabled = !busy, onClick = {
                                approvalTask {
                                    approvedPreview = null
                                    val image = withContext(Dispatchers.IO) {
                                        check(GameplayBudget.allowsOptionalAnalysis(context))
                                        val saved = requireNotNull(ImageApprovalStore.read(approvals, queue, directory, c,
                                            ImageBatchRenderRuntime::verify))
                                        ImageBatchRenderRuntime.previewBytes(saved.bytes, File(c.relative).extension.lowercase(), c) {
                                            ensureActive()
                                            check(GameplayBudget.allowsOptionalAnalysis(context))
                                        }
                                    }
                                    approvedPreview = c.relative to image
                                }
                            }) { Text("저장 승인본 미리보기") }
                        }
                        if (state != ImageApprovalStore.State.MISSING) {
                            TextButton(enabled = !busy, onClick = {
                                approvalTask {
                                    withContext(Dispatchers.IO) { ImageApprovalStore.revoke(approvals, queue, c) }
                                    approvedStates = approvedStates + (c.relative to ImageApprovalStore.State.MISSING)
                                    approvedPreview = null
                                    message = "승인본 삭제 완료"
                                }
                            }) { Text("승인 취소 및 승인본 삭제") }
                        }
                    }
                }
                approvedPreview?.let { (name, image) ->
                    Text("$name · 저장 승인본 축소 보기")
                    Image(bitmap = remember(image) { image.asImageBitmap() }, contentDescription = name,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
                }
                val validCandidates = queue.candidates.filter { approvedStates[it.relative] == ImageApprovalStore.State.VALID }
                Text("현재 유효 표시된 승인 이미지 ${validCandidates.size}개 · 패치 생성 시 다시 확인합니다.")
                TextButton(enabled = !busy && !exportPending && patch == null && validCandidates.isNotEmpty(), onClick = {
                    approvalTask {
                        val selected = validCandidates.toList()
                        patch = withContext(Dispatchers.IO) {
                            val coroutine = kotlinx.coroutines.currentCoroutineContext()
                            fun checkpoint() {
                                coroutine.ensureActive()
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                            }
                            val sources = selected.map { c ->
                                ImagePatchArchive.Source(c.relative, c.digest, c.bytes, c.width, c.height)
                            }
                            ImagePatchArchive.create(queue.root, File(context.cacheDir, "image-patches"), sources, load = { source ->
                                checkpoint()
                                val c = selected.single { it.relative == source.relative }
                                requireNotNull(ImageApprovalStore.read(approvals, queue, directory, c,
                                    ImageBatchRenderRuntime::verify)).bytes
                            }, checkpoint = ::checkpoint)
                        }
                        message = "승인 이미지 ZIP 패치 생성 및 내부 검증 완료."
                    }
                }) { Text("유효 승인 이미지 ZIP 패치 생성") }
                patch?.let { saved ->
                    Text("패치 ${saved.rows.size}개 파일 · 게임에 미적용 · 원본 파일 해시 포함")
                    saved.rows.forEach { Text(it.source.relative) }
                    TextButton(enabled = !busy && !exportPending, onClick = {
                        try {
                            exportPending = true
                            exportPatch.launch("droiddeck-image-patch.zip")
                        } catch (e: Exception) { exportPending = false; message = e.message ?: "파일 선택기 실행 실패" }
                    }) { Text("ZIP 패치 파일 내보내기") }
                    TextButton(enabled = !busy && !exportPending, onClick = {
                        approvalTask {
                            withContext(Dispatchers.IO) { java.nio.file.Files.deleteIfExists(saved.file.toPath()) }
                            patch = null
                            message = "임시 패치 파일 삭제 완료"
                        }
                    }) { Text("임시 ZIP 삭제") }
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
        confirmButton = { TextButton(enabled = !busy && !exportPending, onClick = onDismiss) { Text("목록 닫기") } },
    )
}
