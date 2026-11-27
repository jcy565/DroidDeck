package com.droiddeck.launcher.files

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun GamePatchDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backups = File(context.filesDir, "game-patch-backups")
    val draftDirectory = File(context.filesDir, "translation-drafts")
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
    var translations by remember(file) { mutableStateOf(emptyMap<String, String>()) }
    var pendingExport by remember(file) { mutableStateOf<ByteArray?>(null) }

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
    LaunchedEffect(file) {
        if (!log) {
            val history = withContext(Dispatchers.IO) {
                runCatching { GamePatchStore.history(root, file.name, backups) }.getOrNull()
            }
            backup = history?.restorable
            message = history?.status.orEmpty()
        }
    }
    AlertDialog(onDismissRequest = { if (!busy && pendingExport == null) onDismiss() }, title = { Text(file.name) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text("오프라인 규칙 기반 도우미")
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
                Text("파일 기반 한글 패치 제작: id/text TSV·CSV, 문자열 JSON 사전 · 자동 파일 번역 미지원")
                Text("화면 실시간 번역은 지원하지 않습니다. 초안을 저장해 나중에 이어서 작업할 수 있습니다.")
                TextButton(enabled = !busy && translations.isEmpty(), onClick = { runAction {
                    preview = null
                    translationSource = null
                    translationRows = emptyList()
                    translations = emptyMap()
                    translationIndex = 0
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
                        if (value.length <= 1000) {
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
                        check(GameplayBudget.allows(GameplayBudget.Work.PATCH_WRITE)) { "게임 또는 설치 세션을 종료한 후 다시 시도하세요." }
                        GamePatchStore.restore(root, saved, file.name)
                    }
                    backup = withContext(Dispatchers.IO) { GamePatchStore.findMatchingBackup(root, file.name, backups) }
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
                    check(GameplayBudget.allows(GameplayBudget.Work.PATCH_WRITE)) { "게임 또는 설치 세션을 종료한 후 다시 시도하세요." }
                    GamePatchStore.apply(root, file.name, plan.original.toByteArray(), plan.proposed.toByteArray(), backups)
                }
                preview = null
                message = "백업 후 적용했습니다."
            }
        }) { Text("백업 후 적용") }
    }, dismissButton = { TextButton(enabled = !busy && pendingExport == null, onClick = onDismiss) { Text("닫기") } })
}
