package com.droiddeck.launcher.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import com.droiddeck.ai.core.ModelRelease
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun GamePatchDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val aiAvailability = remember { AiHostBridge.availability() }
    val scope = rememberCoroutineScope()
    val backups = File(context.filesDir, "game-patch-backups")
    val draftDirectory = File(context.filesDir, "translation-drafts")
    val exchangeCheckpointFile = File(context.noBackupFilesDir, "ai-exchange/checkpoint.bin")
    val modelDirectory = File(context.filesDir, "local-agent-models")
    val managedModelDirectory = File(context.filesDir, "managed-ai-models")
    val glossaryDirectory = File(context.filesDir, "ai-data-glossary")
    val root = file.parentFile!!
    val config = file.extension.lowercase() in setOf("ini", "cfg")
    val log = file.extension.equals("log", true)
    val translationTable = file.extension.lowercase() in setOf("tsv", "csv", "json")
    var section by remember(file) { mutableStateOf("") }
    var key by remember(file) { mutableStateOf("") }
    var value by remember(file) { mutableStateOf("") }
    var command by remember(file) { mutableStateOf("") }
    var preview by remember(file) { mutableStateOf<GameConfigPreview.Preview?>(null) }
    var message by remember(file) { mutableStateOf("") }
    var busy by remember(file) { mutableStateOf(false) }
    var backup by remember(file) { mutableStateOf<File?>(null) }
    var translationSource by remember(file) { mutableStateOf<String?>(null) }
    var translationRows by remember(file) { mutableStateOf(emptyList<GamePatchAuthor.Row>()) }
    var translationIndex by remember(file) { mutableIntStateOf(0) }
    var menuCursor by remember(file) { mutableIntStateOf(0) }
    var translations by remember(file) { mutableStateOf(emptyMap<String, String>()) }
    var pendingExport by remember(file) { mutableStateOf<ByteArray?>(null) }
    var agentInstruction by remember(file) { mutableStateOf("") }
    var exchangeInstruction by remember(file) { mutableStateOf("") }
    var exchange by remember(file) { mutableStateOf<ChatGptExchange.Pending?>(null) }
    var exchangeReply by remember(file) { mutableStateOf("") }
    var modelDigest by remember(file) { mutableStateOf("") }
    var pendingModel by remember(file) { mutableStateOf<AiModelUpdates.Pending?>(null) }
    var managedRevision by remember(file) { mutableStateOf<Int?>(null) }
    var retirementChoices by remember(file) { mutableStateOf(emptyList<ModelRelease.Retirement>()) }
    var reviewedRetirement by remember(file) { mutableStateOf<ModelRelease.Retirement?>(null) }
    val managedModels = AiGlossaryUpdates.configured
    var agentJob by remember(file) { mutableStateOf<Job?>(null) }

    fun allowsAgentWork(): Boolean {
        val memory = GameplayBudget.memory(context)
        return AiHostBridge.availability().enabled && GameplayBudget.allowsOptionalAnalysis(context) &&
            memory != null && memory.availableBytes >= 2L * 1024 * 1024 * 1024
    }

    fun runAction(action: suspend () -> Unit) {
        if (busy || pendingExport != null) return
        busy = true
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "작업 실패" }
            finally { busy = false }
        }
    }
    fun CoroutineScope.checkPatchWrite() {
        ensureActive()
        check(GameplayBudget.allows(GameplayBudget.Work.PATCH_WRITE)) { "게임 또는 설치 세션을 종료한 후 다시 시도하세요." }
    }

    suspend fun patchHistory() = withContext(Dispatchers.IO) {
        try {
            if (!GameplayBudget.allowsOptionalAnalysis(context)) return@withContext null
            GamePatchStore.history(root, file.name, backups) {
                ensureActive()
                check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun original() = withContext(Dispatchers.IO) { LocalGameAdvisor.decode(GamePatchStore.readTarget(file)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runAction {
            preview = null
            require(uri.scheme == "content") { "Android 문서 선택기를 사용하세요." }
            val before = original()
            val after = withContext(Dispatchers.IO) {
                val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
                context.contentResolver.openInputStream(uri)?.use { LocalGameAdvisor.replacement(file.name, name, it) }
                    ?: error("선택한 파일을 읽을 수 없습니다.")
            }
            require(after.isNotBlank() && before != after) { "빈 파일이거나 변경 사항이 없습니다." }
            preview = GameConfigPreview.Preview(before, after, emptyList())
            message = "선택한 UTF-8 텍스트로 파일 전체를 교체합니다. 내용을 확인하세요."
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) runAction {
            require(uri.scheme == "content")
            withContext(Dispatchers.IO) {
                val output = context.contentResolver.openOutputStream(uri, "w")
                    ?: error("저장할 파일을 열 수 없습니다.")
                output.use { it.write(payload) }
            }
            message = "패치 ZIP을 저장했습니다. 게임 파일은 변경하지 않았습니다."
        }
    }
    val batchExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val payload = pendingExport
        pendingExport = null
        if (uri != null && payload != null) runAction {
            require(uri.scheme == "content")
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri, "w")?.use { it.write(payload) }
                    ?: error("번역 작업 파일을 저장하지 못했습니다.")
            }
            message = "번역 작업 파일을 저장했습니다. 원본은 변경되지 않았습니다."
        }
    }
    val batchImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runAction {
            require(uri.scheme == "content")
            val source = requireNotNull(translationSource)
            val previous = translations.toMap()
            val merged = withContext(Dispatchers.IO) {
                check(GameplayBudget.allowsOptionalAnalysis(context))
                require(original() == source) { "원본이 변경됐습니다. 다시 추출하세요." }
                val payload = context.contentResolver.openInputStream(uri)?.use {
                    LocalGameAdvisor.decode(GamePatchStore.readLimited(it))
                } ?: error("번역 작업 파일을 읽을 수 없습니다.")
                check(GameplayBudget.allowsOptionalAnalysis(context))
                TranslationBatch.merge(file.canonicalPath, source, payload, previous)
            }
            translations = merged
            preview = null
            message = "일괄 번역 내용을 초안에 반영했습니다. 검토 후 저장하거나 패치를 제작하세요."
        }
    }
    val modelImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && aiAvailability.enabled && LocalAgentRuntime.enabled) runAction {
            AiHostBridge.requireCompatible()
            require(uri.scheme == "content")
            val digest = modelDigest
            withContext(Dispatchers.IO) {
                check(GameplayBudget.allowsOptionalAnalysis(context))
                context.contentResolver.openInputStream(uri)?.use {
                    LocalAgentRuntime.withIdleModelStore {
                        LocalModelStore.install(modelDirectory, digest, it) { GameplayBudget.allowsOptionalAnalysis(context) }
                    }
                } ?: error("모델 파일을 읽을 수 없습니다.")
            }
            message = "모델 체크섬 확인 및 가져오기 완료. 실제 모델 호환성은 실행 시 확인합니다."
        }
    }
    val modelManifestImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && aiAvailability.enabled && LocalAgentRuntime.enabled && managedModels) runAction {
            pendingModel = null
            require(uri.scheme == "content")
            pendingModel = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use {
                    AiModelUpdates.inspect(it) { GameplayBudget.allowsOptionalAnalysis(context) }
                } ?: error("모델 명세를 읽을 수 없습니다.")
            }
            message = "서명된 모델 명세를 검증했습니다. 아래 정보를 확인하고 모델 파일을 선택하세요."
        }
    }
    val managedModelImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && aiAvailability.enabled && LocalAgentRuntime.enabled && managedModels) runAction {
            val pending = requireNotNull(pendingModel)
            require(uri.scheme == "content")
            val manifest = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use {
                    AiModelUpdates.install(managedModelDirectory, pending, it) { GameplayBudget.allowsOptionalAnalysis(context) }
                } ?: error("모델 파일을 읽을 수 없습니다.")
            }
            managedRevision = manifest.revision
            pendingModel = null
            retirementChoices = emptyList()
            reviewedRetirement = null
            message = "모델 버전 ${manifest.revision}의 서명과 파일 해시를 확인했습니다. 실제 추론 호환성은 아직 검증되지 않았습니다."
        }
    }
    val glossaryImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && aiAvailability.enabled && AiGlossaryUpdates.configured) runAction {
            require(uri.scheme == "content")
            val bundle = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use {
                    AiGlossaryUpdates.install(glossaryDirectory, it) { GameplayBudget.allowsOptionalAnalysis(context) }
                } ?: error("AI 데이터 파일을 읽을 수 없습니다.")
            }
            menuCursor = 0
            message = "서명된 메뉴 사전 버전 ${bundle.revision}을 적용했습니다. 게임 파일과 초안은 변경되지 않았습니다."
        }
    }
    LaunchedEffect(file) {
        if (aiAvailability.enabled && LocalAgentRuntime.enabled && managedModels) {
            managedRevision = withContext(Dispatchers.IO) {
                runCatching { AiModelUpdates.active(managedModelDirectory)?.revision }.getOrNull()
            }
        }
        if (!log) {
            val history = patchHistory()
            backup = history?.restorable
            message = history?.status.orEmpty()
        }
    }
    AlertDialog(onDismissRequest = { if (!busy && pendingExport == null) onDismiss() }, title = { Text(file.name) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("오프라인 규칙 기반 도우미")
            if (config || log || translationTable) {
                Text("엔진 API ${AiHostBridge.HOST_API} · AI 모듈 ${aiAvailability.version ?: "없음"}")
                aiAvailability.reason?.let { Text(it + " 기본 파일 도구는 계속 사용할 수 있습니다.") }
            }
            if (aiAvailability.enabled && (config || log || translationTable)) {
                Text("ChatGPT 앱 연동 · 수동 작업 교환")
                Text("별도 API 키나 로컬 모델 없이 사용합니다. 요청에 선택한 파일 내용이 포함됩니다. 개인정보, 계정 정보, 저작권 제한 자료를 공유하지 마세요. 자동 전송 또는 자동 익명화는 하지 않습니다.")
                OutlinedTextField(exchangeInstruction, { if (it.length <= GameAgent.MAX_REQUEST_CHARS) {
                    exchangeInstruction = it
                    exchange = null
                    exchangeReply = ""
                } }, enabled = !busy, label = { Text("ChatGPT 작업 요청") })
                TextButton(enabled = !busy && pendingExport == null && exchangeInstruction.isNotBlank() &&
                    (!translationTable || translationRows.isNotEmpty()), onClick = { runAction {
                    exchange = null
                    exchangeReply = ""
                    val instruction = exchangeInstruction
                    val edits = if (translationTable) translations.toMap() else emptyMap()
                    val start = if (translationTable) translationIndex else 0
                    val expected = translationSource
                    exchange = withContext(Dispatchers.IO) {
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                        val source = if (log) LocalGameAdvisor.decode(GamePatchStore.readLog(file)) else original()
                        if (translationTable) require(source == expected) { "원본이 변경됐습니다. 다시 추출하세요." }
                        ChatGptExchange.create(file.canonicalPath, GameAgent.Request(instruction, file.extension, source, edits, start))
                    }
                    message = "아래 요청 전문을 검토한 후 공유하세요. 실제 전송은 직접 선택합니다."
                } }) { Text("AI 작업 요청 생성") }
                Text("앱 전환 후 작업을 재개하려면 요청 정보를 저장하세요. 요청 문구와 식별자·해시만 이 기기의 백업 제외 앱 저장소에 보관합니다. 원본·로그·번역문은 저장하지 않습니다. 번역 초안은 별도로 저장해야 합니다.")
                TextButton(enabled = !busy && pendingExport == null && (!translationTable || translationRows.isNotEmpty()),
                    onClick = { runAction {
                        val edits = if (translationTable) translations.toMap() else emptyMap()
                        val expected = translationSource
                        val restored = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            val saved = ExchangeCheckpoint.load(exchangeCheckpointFile) ?: error("저장된 AI 요청이 없습니다.")
                            val source = if (log) LocalGameAdvisor.decode(GamePatchStore.readLog(file)) else original()
                            if (translationTable) require(source == expected) { "원본이 변경됐습니다. 다시 추출하세요." }
                            ChatGptExchange.restore(saved, file.canonicalPath, file.extension, source, edits)
                        }
                        exchangeInstruction = restored.request.instruction
                        if (translationTable) translationIndex = restored.request.start
                        exchangeReply = ""
                        exchange = restored
                        message = "저장된 AI 요청을 복구했습니다. 기존 ChatGPT 응답을 붙여넣으세요."
                    } }) { Text("저장된 AI 요청 재개") }
                TextButton(enabled = !busy, onClick = { runAction {
                    val removed = withContext(Dispatchers.IO) {
                        ExchangeCheckpoint.discard(exchangeCheckpointFile)
                    }
                    message = if (removed) "저장된 AI 요청 정보를 삭제했습니다." else "저장된 AI 요청이 없습니다."
                } }) { Text("저장된 AI 요청 정보 지우기 (손상된 정보 포함)") }
                exchange?.let { pending ->
                    TextButton(enabled = !busy && pendingExport == null, onClick = { runAction {
                        val edits = if (translationTable) translations.toMap() else emptyMap()
                        withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            val source = if (log) LocalGameAdvisor.decode(GamePatchStore.readLog(file)) else original()
                            val saved = ChatGptExchange.checkpoint(pending, file.canonicalPath, source, edits)
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            ExchangeCheckpoint.save(exchangeCheckpointFile, saved)
                        }
                        message = "현재 요청의 재개 정보를 저장했습니다. 이전 저장 요청은 교체됐습니다."
                    } }) { Text("현재 요청 저장 (이전 저장 요청 교체)") }
                    OutlinedTextField(pending.prompt, {}, readOnly = true,
                        label = { Text("공유할 요청 전문") }, maxLines = 8)
                    TextButton(enabled = !busy && pendingExport == null, onClick = {
                        try {
                            AiHostBridge.requireCompatible()
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_TEXT, pending.prompt), "ChatGPT로 공유"))
                        } catch (_: Exception) { message = "공유 화면을 열지 못했습니다. 요청 전문을 직접 복사하세요." }
                    }) { Text("검토 후 요청 공유") }
                    OutlinedTextField(exchangeReply, { if (it.length <= GameAgent.MAX_OUTPUT_CHARS) exchangeReply = it },
                        enabled = !busy, label = { Text("ChatGPT의 JSON 응답 붙여넣기") }, maxLines = 8)
                    TextButton(enabled = !busy && pendingExport == null && exchangeReply.isNotBlank(), onClick = { runAction {
                        val reply = exchangeReply
                        val edits = if (translationTable) translations.toMap() else emptyMap()
                        val result = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            val source = if (log) LocalGameAdvisor.decode(GamePatchStore.readLog(file)) else original()
                            val result = ChatGptExchange.accept(pending, file.canonicalPath, source, edits, reply)
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            if (!log) require(original() == source) { "원본이 변경됐습니다." }
                            result
                        }
                        when (result) {
                            is GameAgent.Result.Patch -> { preview = result.preview; message = result.explanation }
                            is GameAgent.Result.Translation -> {
                                translations = result.edits
                                translationIndex = result.next.coerceAtMost(translationRows.lastIndex)
                                preview = null
                                message = result.explanation + " · 초안을 검토하고 저장하세요."
                            }
                            is GameAgent.Result.Advice -> { preview = null; message = result.explanation + "\n" + result.evidence }
                        }
                        val cleanupFailed = withContext(Dispatchers.IO) {
                            runCatching { ExchangeCheckpoint.clear(exchangeCheckpointFile, pending.id) }.isFailure
                        }
                        if (cleanupFailed) message += " · 저장된 요청 정보 정리 실패"
                        exchange = null
                        exchangeReply = ""
                    } }) { Text("AI 결과 검증 및 초안 반영") }
                    TextButton(enabled = !busy, onClick = { exchange = null; exchangeReply = "" }) { Text("AI 작업 취소") }
                }
            }
            if (aiAvailability.enabled && LocalAgentRuntime.enabled && (config || log || translationTable)) {
                Text("실험용 온디바이스 AI · 검증되지 않은 모델 실행 기능")
                Text("신뢰할 수 있는 배포처의 호환 .task 모델과 SHA-256이 필요합니다. SDK 진단 정보 수집 여부는 아직 검증되지 않았습니다.")
                if (managedModels) {
                    Text("서명된 독립 모델 · 활성 버전: ${managedRevision ?: "없음 또는 비호환"}")
                    Text("최대 두 개의 서로 다른 모델 파일 보관. 기존 모델은 자동 삭제하지 않습니다.")
                    TextButton(enabled = !busy && pendingExport == null, onClick = {
                        modelManifestImporter.launch(arrayOf("*/*"))
                    }) { Text("서명된 모델 명세 선택") }
                    pendingModel?.let { pending ->
                        Text("버전 ${pending.manifest.revision} · ${pending.manifest.bytes}바이트")
                        Text("SHA-256: ${pending.manifest.sha256}")
                        TextButton(enabled = !busy && pendingExport == null, onClick = {
                            managedModelImporter.launch(arrayOf("*/*"))
                        }) { Text("검토 후 모델 파일 가져오기") }
                        TextButton(enabled = !busy, onClick = { pendingModel = null }) { Text("모델 업데이트 취소") }
                    }
                    TextButton(enabled = !busy && pendingExport == null, onClick = { runAction {
                        val prior = withContext(Dispatchers.IO) {
                            AiModelUpdates.rollback(managedModelDirectory) { GameplayBudget.allowsOptionalAnalysis(context) }
                        }
                        managedRevision = prior.revision
                        pendingModel = null
                        retirementChoices = emptyList()
                        reviewedRetirement = null
                        message = "이전 모델 버전 ${prior.revision}으로 복구했습니다."
                    } }) { Text("이전 AI 모델 복구") }
                    TextButton(enabled = !busy && pendingExport == null, onClick = { runAction {
                        retirementChoices = emptyList()
                        reviewedRetirement = null
                        retirementChoices = withContext(Dispatchers.IO) {
                            AiModelUpdates.retirementCandidates(managedModelDirectory) { GameplayBudget.allowsOptionalAnalysis(context) }
                        }
                        message = if (retirementChoices.isEmpty()) "정리할 비활성 모델이 없습니다." else "정리할 모델을 선택하세요. 현재 활성 모델은 제외됩니다."
                    } }) { Text("비활성 모델 저장공간 확인") }
                    retirementChoices.forEach { candidate ->
                        TextButton(enabled = !busy && pendingExport == null, onClick = { reviewedRetirement = candidate }) {
                            Text((if (candidate.previousRevision > 0) "복구용 모델 v${candidate.previousRevision}" else "미참조 모델") +
                                " · ${candidate.bytes}바이트 · ${candidate.sha256.take(12)}")
                        }
                    }
                    reviewedRetirement?.let { candidate ->
                        Text("삭제 대상 SHA-256: ${candidate.sha256}")
                        Text(if (candidate.previousRevision > 0)
                            "이 모델을 정리하면 현재 모델은 유지되지만 이전 버전 복구가 불가능해집니다. 새 모델 설치가 실패해도 삭제된 복구 모델은 돌아오지 않습니다."
                            else "현재 모델이나 복구용 모델이 참조하지 않는 파일입니다.")
                        TextButton(enabled = !busy && pendingExport == null, onClick = { runAction {
                            val result = withContext(Dispatchers.IO) {
                                AiModelUpdates.retire(managedModelDirectory, candidate) { GameplayBudget.allowsOptionalAnalysis(context) }
                            }
                            retirementChoices = emptyList()
                            reviewedRetirement = null
                            message = when {
                                result.fileDeleted -> "선택한 모델 파일을 정리했습니다. 현재 활성 모델은 유지됩니다."
                                result.rollbackRemoved -> "복구용 참조는 해제됐지만 파일은 남아 있습니다. 다시 정리 대상을 확인하세요."
                                else -> "파일을 정리하지 못했습니다. 다시 확인하세요."
                            }
                        } }) { Text("확인 후 선택한 모델 삭제") }
                        TextButton(enabled = !busy, onClick = { reviewedRetirement = null }) { Text("삭제 취소") }
                    }
                } else {
                    Text("배포용 신뢰 키 미설정: 아래는 실험용 체크섬 가져오기입니다.")
                    OutlinedTextField(modelDigest, { if (it.length <= 64) modelDigest = it },
                        label = { Text("모델 SHA-256") }, enabled = !busy, singleLine = true)
                    TextButton(enabled = !busy && pendingExport == null && LocalModelStore.validDigest(modelDigest),
                        onClick = { modelImporter.launch(arrayOf("*/*")) }) { Text("로컬 모델 가져오기") }
                }
                OutlinedTextField(agentInstruction, { if (it.length <= GameAgent.MAX_REQUEST_CHARS) agentInstruction = it },
                    enabled = !busy, label = { Text("AI에게 작업 요청") })
                TextButton(enabled = !busy && pendingExport == null && agentInstruction.isNotBlank() &&
                    (if (managedModels) managedRevision != null else LocalModelStore.validDigest(modelDigest)) &&
                    (!translationTable || translationRows.isNotEmpty()),
                    onClick = {
                        if (!busy && pendingExport == null) {
                            busy = true
                            preview = null
                            val digest = modelDigest
                            val instruction = agentInstruction
                            val edits = if (translationTable) translations.toMap() else emptyMap()
                            val start = if (translationTable) translationIndex else 0
                            val expected = translationSource
                            agentJob = scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        check(allowsAgentWork())
                                        val source = if (log) LocalGameAdvisor.decode(GamePatchStore.readLog(file)) else original()
                                        if (translationTable) require(source == expected) { "원본이 변경됐습니다. 다시 추출하세요." }
                                        val request = GameAgent.Request(instruction, file.extension, source, edits, start)
                                        val result = GameAgent.run(request, ::allowsAgentWork,
                                            { LocalAgentRuntime.openSelected(context, ::allowsAgentWork) {
                                                if (managedModels) AiModelUpdates.verifiedActive(managedModelDirectory, ::allowsAgentWork)
                                                else LocalModelStore.verified(modelDirectory, digest, ::allowsAgentWork)
                                            } }, 120_000)
                                        check(allowsAgentWork())
                                        if (!log) require(original() == source) { "원본이 변경됐습니다. 결과를 반영하지 않았습니다." }
                                        result
                                    }
                                    when (result) {
                                        is GameAgent.Result.Patch -> {
                                            preview = result.preview
                                            message = "AI 설정 제안 (검토 필요): " + result.explanation
                                        }
                                        is GameAgent.Result.Translation -> {
                                            translations = result.edits
                                            translationIndex = result.next.coerceAtMost(translationRows.lastIndex)
                                            preview = null
                                            message = "AI 번역 초안 (검토 필요): " + result.explanation
                                        }
                                        is GameAgent.Result.Advice -> {
                                            message = "AI 추정: " + result.explanation + "\n규칙 기반 근거: " + result.evidence
                                        }
                                    }
                                } catch (_: TimeoutCancellationException) {
                                    message = "AI 응답 시간이 초과됐습니다. 모델 종료를 요청했습니다."
                                } catch (e: CancellationException) {
                                    message = "AI 작업을 취소했습니다. 모델 종료를 요청했습니다."
                                    throw e
                                } catch (e: Exception) {
                                    message = e.message ?: "AI 작업 실패"
                                } finally {
                                    busy = false
                                    agentJob = null
                                }
                            }
                        }
                    }) { Text("AI 실행 · 초안 생성") }
                TextButton(enabled = agentJob != null, onClick = { agentJob?.cancel() }) { Text("AI 작업 취소") }
                if (translationTable) Text("번역 대상 추출 후 현재 문장부터 최대 5개를 AI에 요청합니다.")
            }
            if (config) {
                OutlinedTextField(section, { section = it; preview = null }, label = { Text("INI 섹션 (선택)") }, enabled = !busy, singleLine = true)
                OutlinedTextField(key, { key = it; preview = null }, label = { Text("기존 설정 키") }, enabled = !busy, singleLine = true)
                OutlinedTextField(value, { value = it; preview = null }, label = { Text("새 값") }, enabled = !busy, singleLine = true)
                TextButton(enabled = !busy && key.isNotBlank(), onClick = { runAction {
                    preview = null
                    preview = GameConfigPreview.preview(original(), section.trim().ifEmpty { null }, mapOf(key.trim() to value))
                    message = "변경 전후 값을 확인하세요."
                } }) { Text("설정 미리보기") }
            }
            if (config || log) {
                OutlinedTextField(command, { command = it; preview = null }, enabled = !busy, singleLine = true,
                    label = { Text(if (log) "예: 검은 화면 원인 분석" else "예: 720p, 900p 균형, 배터리 30 FPS") })
                TextButton(enabled = !busy, onClick = { runAction {
                    preview = null
                    val input = command.trim()
                    if (log && (input.isEmpty() || listOf("분석", "오류", "검은", "원인").any { it in input })) {
                        message = withContext(Dispatchers.IO) {
                            if (!GameplayBudget.allowsOptionalAnalysis(context)) {
                                "게임 실행 중이거나 메모리 상태가 충분하지 않아 로그 분석을 보류합니다. 세션 종료 후 다시 시도하세요."
                            } else {
                                val bytes = GamePatchStore.readLog(file)
                                if (!GameplayBudget.allowsOptionalAnalysis(context)) {
                                    "실행 상태 또는 메모리 상태가 변경되어 분석을 보류합니다."
                                } else LocalGameAdvisor.diagnose(LocalGameAdvisor.decode(bytes))
                            }
                        }
                    } else if (config && (input.isEmpty() || listOf("720", "900", "균형", "30", "배터리", "최적화", "해상도").any { it in input })) {
                        check(withContext(Dispatchers.IO) { GameplayBudget.allowsOptionalAnalysis(context) }) {
                            "게임 세션 종료 및 메모리 확보 후 다시 시도하세요."
                        }
                        preview = withContext(Dispatchers.IO) {
                            val source = original()
                            check(GameplayBudget.allowsOptionalAnalysis(context)) { "최적화 분석을 보류합니다." }
                            LocalGameAdvisor.optimize(source, input)
                        }
                        message = "설정 한 그룹의 비교 계획입니다. 적용 후 같은 장면에서 측정하세요."
                    } else message = "이 파일에서 지원하는 명령 예시를 확인하세요."
                } }) { Text(if (log) "로그 분석" else "최적화 계획") }
            }
            if (config) {
                Text("간단한 설정형 모드: 모드: Speed=1.5 (실제 존재하는 키만 지원)")
                TextButton(enabled = !busy, onClick = { runAction {
                    preview = null
                    preview = withContext(Dispatchers.IO) {
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                        val source = original()
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                        GamePatchAuthor.mod(source, section.trim().ifEmpty { null }, command)
                    }
                    message = "설정형 모드 초안입니다. 실제 게임 동작은 확인되지 않았습니다."
                } }) { Text("간단한 모드 제작") }
            }
            if (translationTable) {
                Text("파일 기반 한글 패치 제작: id/text TSV·CSV, 문자열 JSON 사전 · 최대 ${TranslationLimits.MAX_ROWS}문장 · 메뉴 사전 번역 지원, 신경망 번역 미지원")
                Text("화면 실시간 번역은 지원하지 않습니다. 초안을 저장해 나중에 이어서 작업할 수 있습니다.")
                if (aiAvailability.enabled && AiGlossaryUpdates.configured) {
                    Text("AI 데이터는 서명된 메뉴 사전만 별도로 업데이트할 수 있습니다.")
                    TextButton(enabled = !busy && pendingExport == null, onClick = {
                        glossaryImporter.launch(arrayOf("application/octet-stream", "*/*"))
                    }) { Text("서명된 AI 메뉴 사전 가져오기") }
                    TextButton(enabled = !busy && pendingExport == null, onClick = { runAction {
                        val prior = withContext(Dispatchers.IO) {
                            AiGlossaryUpdates.rollback(glossaryDirectory) { GameplayBudget.allowsOptionalAnalysis(context) }
                        }
                        menuCursor = 0
                        message = "메뉴 사전 버전 ${prior.revision}으로 복구했습니다. 기존 번역 초안은 유지됩니다."
                    } }) { Text("이전 AI 메뉴 사전 복구") }
                }
                TextButton(enabled = !busy && translations.isEmpty(), onClick = { runAction {
                    preview = null
                    translationSource = null
                    translationRows = emptyList()
                    translations = emptyMap()
                    translationIndex = 0
                    menuCursor = 0
                    val source = withContext(Dispatchers.IO) {
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                        original()
                    }
                    val (rows, saved) = withContext(Dispatchers.IO) {
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                        require(GamePatchAuthor.matchesTranslationExtension(source, file.extension)) { "파일 확장자와 실제 번역 형식이 다릅니다." }
                        val rows = GamePatchAuthor.extract(source)
                        rows to TranslationDraftStore.load(draftDirectory, file.canonicalPath, source)
                    }
                    translationSource = source
                    translationRows = rows
                    translations = saved.orEmpty()
                    message = if (saved == null) "번역 대상 ${rows.size}개를 읽었습니다."
                        else "저장한 번역 초안 ${saved.size}개를 불러왔습니다."
                } }) { Text("번역 대상 추출") }
                translationRows.getOrNull(translationIndex)?.let { row ->
                    Text("${translationIndex + 1} / ${translationRows.size} · ${row.id}")
                    Text(row.text)
                    Text("입력한 번역: ${translations.size} / ${translationRows.size} (최종 검증 전)")
                    Text("오프라인 메뉴 사전: 알려진 영어 메뉴 문구만 번역합니다. 일반 대사 및 신경망 번역은 지원하지 않습니다.")
                    Text("메뉴 사전 처리 위치: ${menuCursor} / ${translationRows.size}")
                    TextButton(enabled = !busy && pendingExport == null && menuCursor < translationRows.size,
                        onClick = { runAction {
                            val source = requireNotNull(translationSource)
                            val edits = translations.toMap()
                            val start = menuCursor
                            val result = withContext(Dispatchers.IO) {
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                require(original() == source) { "원본이 변경됐습니다. 다시 추출하세요." }
                                val updated = if (aiAvailability.enabled) runCatching {
                                    AiGlossaryUpdates.active(glossaryDirectory)?.entries.orEmpty()
                                }.getOrDefault(emptyMap()) else emptyMap()
                                val result = TranslationEngine.run(source, edits, startIndex = start,
                                    allowed = { GameplayBudget.allowsOptionalAnalysis(context) },
                                    providerFactory = { MenuTranslationProvider(updated) })
                                check(GameplayBudget.allowsOptionalAnalysis(context)) { "게임 실행 또는 메모리 상태 변경으로 반영을 보류했습니다." }
                                require(original() == source) { "원본이 변경됐습니다. 결과를 반영하지 않았습니다." }
                                result
                            }
                            translations = result.edits
                            menuCursor = result.nextIndex
                            preview = null
                            message = "메뉴 사전 번역 ${result.added}개, 미지원·검증 실패 ${result.rejected}개, 기존 번역 ${result.skipped}개. 문맥을 검토하고 초안을 저장하세요."
                        } }) { Text("다음 최대 25문장 메뉴 번역") }
                    TextButton(enabled = !busy && pendingExport == null && menuCursor > 0,
                        onClick = { menuCursor = 0 }) { Text("메뉴 사전 처리 위치 초기화") }
                    TextButton(enabled = !busy && translations.isNotEmpty(), onClick = { runAction {
                        val source = requireNotNull(translationSource)
                        val edits = translations.toMap()
                        val result = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            require(original() == source) { "원본이 변경됐습니다. 다시 추출하세요." }
                            val result = TranslationReuse.fill(source, edits)
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            result
                        }
                        translations = result.edits
                        preview = null
                        message = "동일 원문 번역 재사용 ${result.added}개, 충돌·검증 보류 ${result.conflicts}개. 문맥에 맞는지 확인하세요."
                    } }) { Text("동일 원문 번역 재사용") }
                    TextButton(enabled = !busy, onClick = { runAction {
                        val source = requireNotNull(translationSource)
                        val edits = translations.toMap()
                        val review = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            require(original() == source) { "원본이 변경됐습니다. 다시 추출하세요." }
                            TranslationReuse.review(source, edits)
                        }
                        review.nextUnfinished(translationRows.map { it.id }, translationIndex)?.let {
                            translationIndex = it
                        }
                        message = "형식 통과 ${review.count(TranslationReuse.Status.READY)}개, " +
                            "미입력 ${review.count(TranslationReuse.Status.MISSING)}개, " +
                            "원문 그대로 ${review.count(TranslationReuse.Status.UNCHANGED)}개, " +
                            "형식 오류 ${review.count(TranslationReuse.Status.INVALID)}개. 번역 품질은 직접 검토하세요."
                    } }) { Text("번역 검토·다음 미완료 문장") }
                    Text("번역 작업 JSON을 내보내고 row. 항목의 값만 번역한 후 불러올 수 있습니다. 파일은 자동 전송되지 않습니다.")
                    TextButton(enabled = !busy && pendingExport == null, onClick = { runAction {
                        val source = requireNotNull(translationSource)
                        pendingExport = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            require(original() == source) { "원본이 변경됐습니다. 다시 추출하세요." }
                            TranslationBatch.export(file.canonicalPath, source).toByteArray(Charsets.UTF_8)
                        }
                        try {
                            batchExporter.launch(file.name + ".translation.json")
                        } catch (e: Exception) {
                            pendingExport = null
                            throw e
                        }
                    } }) { Text("번역 작업 파일 내보내기") }
                    TextButton(enabled = !busy && pendingExport == null, onClick = {
                        batchImporter.launch(arrayOf("application/json", "text/plain"))
                    }) { Text("일괄 번역 파일 불러오기") }
                    OutlinedTextField(translations[row.id].orEmpty(), { value ->
                        if (value.length <= TranslationLimits.MAX_TEXT_CHARS) {
                            translations = if (value.isEmpty()) translations - row.id
                                else translations + (row.id to value)
                            preview = null
                        }
                    }, label = { Text("번역문") }, enabled = !busy)
                    TextButton(enabled = !busy && translationIndex > 0, onClick = { translationIndex-- }) { Text("이전 문장") }
                    TextButton(enabled = !busy && translationIndex + 1 < translationRows.size, onClick = { translationIndex++ }) { Text("다음 문장") }
                    TextButton(enabled = !busy && translations.isNotEmpty(), onClick = { runAction {
                        val source = requireNotNull(translationSource)
                        val edits = translations.toMap()
                        withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            require(original() == source) { "원본이 변경되어 초안 저장을 중단했습니다." }
                            TranslationDraftStore.save(draftDirectory, file.canonicalPath, source, edits)
                        }
                        message = "번역 초안을 저장했습니다. 게임 파일은 변경되지 않았습니다."
                    } }) { Text("번역 초안 저장") }
                    TextButton(enabled = !busy && translations.isNotEmpty(), onClick = { runAction {
                        preview = null
                        val source = requireNotNull(translationSource)
                        val edits = translations.toMap()
                        preview = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allowsOptionalAnalysis(context))
                            require(original() == source) { "원본이 변경됐습니다. 다시 추출하세요." }
                            GamePatchAuthor.translation(source, edits)
                        }
                        message = "번역 패치 초안입니다. 원본과 교체 내용을 확인하세요."
                    } }) { Text("한글 패치 미리보기") }
                }
            }
            if (!log) TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text("모드·한글 패치 선택") }
            TextButton(enabled = !busy && pendingExport == null && preview?.let { it.original != it.proposed } == true,
                onClick = { runAction {
                    val plan = requireNotNull(preview)
                    pendingExport = withContext(Dispatchers.IO) {
                        check(GameplayBudget.allowsOptionalAnalysis(context))
                        require(original() == plan.original) { "원본이 변경됐습니다. 다시 미리보기 하세요." }
                        GamePatchAuthor.zip(file.name, plan)
                    }
                    try {
                        exporter.launch(file.name + ".patch.zip")
                    } catch (e: Exception) {
                        pendingExport = null
                        throw e
                    }
                } }) { Text("패치 ZIP 내보내기") }
            preview?.let { plan ->
                if (plan.changes.isEmpty() && plan.original != plan.proposed) {
                    var page by remember(plan) { mutableIntStateOf(0) }
                    val pages = (maxOf(plan.original.length, plan.proposed.length) + 1599) / 1600
                    val offset = page * 1600
                    Text("원본\n" + plan.original.drop(offset).take(1600))
                    Text("교체 내용\n" + plan.proposed.drop(offset).take(1600))
                    Text("${page + 1} / $pages")
                    TextButton(enabled = page > 0, onClick = { page-- }) { Text("이전") }
                    TextButton(enabled = page + 1 < pages, onClick = { page++ }) { Text("다음") }
                } else plan.changes.forEach { Text("${it.key}: ${it.before} → ${it.after}") }
            }
            Text(message)
            backup?.let { saved ->
                TextButton(enabled = !busy, onClick = { runAction {
                    withContext(Dispatchers.IO) {
                        GamePatchStore.restore(root, saved, file.name) { checkPatchWrite() }
                    }
                    backup = patchHistory()?.restorable
                    preview = null
                    message = "복원했습니다."
                } }) { Text("최근 변경 복원") }
            }
        }
    }, confirmButton = {
        val plan = preview
        TextButton(enabled = !busy && pendingExport == null && plan != null && plan.original != plan.proposed, onClick = {
            if (plan != null) runAction {
                backup = withContext(Dispatchers.IO) {
                    GamePatchStore.apply(root, file.name, plan.original.toByteArray(), plan.proposed.toByteArray(), backups) {
                        checkPatchWrite()
                    }
                }
                preview = null
                message = "백업 후 적용했습니다."
            }
        }) { Text("백업 후 적용") }
    }, dismissButton = { TextButton(enabled = !busy && pendingExport == null, onClick = onDismiss) { Text("닫기") } })
}
