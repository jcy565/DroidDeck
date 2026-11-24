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
    val root = file.parentFile!!
    val config = file.extension.lowercase() in setOf("ini", "cfg")
    val log = file.extension.equals("log", true)
    var section by remember(file) { mutableStateOf("") }
    var key by remember(file) { mutableStateOf("") }
    var value by remember(file) { mutableStateOf("") }
    var command by remember(file) { mutableStateOf("") }
    var preview by remember(file) { mutableStateOf<GameConfigPreview.Preview?>(null) }
    var message by remember(file) { mutableStateOf("") }
    var busy by remember(file) { mutableStateOf(false) }
    var backup by remember(file) { mutableStateOf<File?>(null) }

    fun runAction(action: suspend () -> Unit) {
        if (busy) return
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
    LaunchedEffect(file) {
        if (!log) {
            val history = withContext(Dispatchers.IO) {
                runCatching { GamePatchStore.history(root, file.name, backups) }.getOrNull()
            }
            backup = history?.restorable
            message = history?.status.orEmpty()
        }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(file.name) }, text = {
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
            if (!log) TextButton(enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) }) { Text("모드·한글 패치 선택") }
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
        TextButton(enabled = !busy && plan != null && plan.original != plan.proposed, onClick = {
            if (plan != null) runAction {
                backup = withContext(Dispatchers.IO) {
                    check(GameplayBudget.allows(GameplayBudget.Work.PATCH_WRITE)) { "게임 또는 설치 세션을 종료한 후 다시 시도하세요." }
                    GamePatchStore.apply(root, file.name, plan.original.toByteArray(), plan.proposed.toByteArray(), backups)
                }
                preview = null
                message = "백업 후 적용했습니다."
            }
        }) { Text("백업 후 적용") }
    }, dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } })
}
