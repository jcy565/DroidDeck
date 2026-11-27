package com.droiddeck.launcher.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ImageTranslationDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember(file) { mutableStateOf("") }
    var translated by remember(file) { mutableStateOf("") }
    var script by remember(file) { mutableStateOf(ImageTranslationInput.Script.LATIN) }
    var rotation by remember(file) { mutableStateOf(ImageTranslationInput.Rotation.NONE) }
    var busy by remember(file) { mutableStateOf(false) }
    var message by remember(file) { mutableStateOf("") }

    fun allowed(): Boolean = GameplayBudget.allowsOptionalAnalysis(context)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("이미지 번역 · ${file.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("PNG·JPEG·WebP 정지 이미지의 문자 추출과 번역문 검토를 지원합니다. 원본 이미지나 게임 파일은 수정하지 않습니다.")
                Text("이미지 인식은 영어·일본어·중국어용 실험 기능입니다. EXIF 회전은 자동 보정하지 않으며, 잘못 인식된 문장은 직접 수정하세요.")
                if (ImageOcrRuntime.enabled) {
                    Text("인식할 문자 선택: ${script.label}")
                    ImageTranslationInput.Script.values().forEach { option ->
                        TextButton(enabled = !busy && option != script, onClick = { script = option }) {
                            Text(option.label)
                        }
                    }
                    Text("인식 회전 각도: ${rotation.degrees}°")
                    TextButton(enabled = !busy, onClick = {
                        val options = ImageTranslationInput.Rotation.values()
                        rotation = options[(rotation.ordinal + 1) % options.size]
                    }) { Text("시계 방향 90° 회전") }
                    Text("다시 인식하면 현재 텍스트와 번역 초안이 교체됩니다. 필요하면 먼저 복사하세요.")
                    Text("Google ML Kit가 기기에서 이미지 문자를 인식합니다. SDK의 사용량·성능 통계가 Google로 전송될 수 있습니다. 이미지나 인식 결과는 ML Kit 서버로 전송되지 않습니다.")
                    TextButton(enabled = !busy, onClick = {
                        val selectedScript = script
                        val selectedRotation = rotation
                        busy = true
                        message = ""
                        scope.launch {
                            try {
                                val recognized = withContext(Dispatchers.IO) {
                                    ImageOcrRuntime.recognize(file, selectedScript, selectedRotation, ::allowed)
                                }
                                source = recognized
                                translated = ""
                                message = "인식된 글자를 확인한 뒤 번역하세요."
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { message = e.message ?: "이미지 인식 실패" }
                            finally { busy = false }
                        }
                    }) { Text(if (busy) "이미지 인식 중…" else if (source.isNotBlank() || translated.isNotBlank()) "다시 인식 (현재 초안 교체)" else "이미지에서 글자 추출") }
                } else {
                    Text("현재 빌드에는 실험용 OCR이 포함되지 않았습니다. 추출한 글자를 직접 붙여넣어 번역할 수 있습니다.")
                }
                OutlinedTextField(source, { value ->
                    if (value.length <= ImageTranslationInput.MAX_TEXT) {
                        source = value
                        translated = ""
                    }
                }, enabled = !busy, label = { Text("추출한 글자 · 수정 가능") }, maxLines = 8)
                Text("원본 이미지가 아닌 위 텍스트만 공유 대상으로 전달합니다. 개인정보나 민감한 내용이 없는지 먼저 검토하세요.")
                TextButton(enabled = !busy && source.isNotBlank(), onClick = {
                    try {
                        check(allowed()) { "게임 또는 메모리 상태 때문에 공유를 보류했습니다." }
                        AiHostBridge.requireCompatible()
                        val prompt = ImageTranslationInput.prompt(source)
                        context.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, prompt), "ChatGPT로 번역 요청"))
                        message = "ChatGPT에서 번역한 결과를 아래에 붙여넣으세요."
                    } catch (e: Exception) { message = e.message ?: "공유 화면을 열지 못했습니다." }
                }) { Text("검토한 텍스트 번역 요청") }
                OutlinedTextField(translated, { value ->
                    if (value.length <= ImageTranslationInput.MAX_TEXT) translated = value
                }, enabled = !busy, label = { Text("한국어 번역문 붙여넣기 · 수정 가능") }, maxLines = 8)
                TextButton(enabled = !busy && translated.isNotBlank(), onClick = {
                    try {
                        val text = ImageTranslationInput.validateText(translated)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("이미지 번역", text))
                        message = "번역문을 복사했습니다."
                    } catch (e: Exception) { message = e.message ?: "복사 실패" }
                }) { Text("번역문 복사") }
                if (message.isNotEmpty()) Text(message)
                Text("이 화면의 번역문은 임시 초안입니다. 닫기 전에 필요한 내용을 복사하세요. 이미지 글자 교체 및 실시간 화면 번역은 아직 지원하지 않습니다.")
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } },
    )
}
