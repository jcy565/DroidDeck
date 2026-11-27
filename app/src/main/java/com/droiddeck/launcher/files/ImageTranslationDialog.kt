package com.droiddeck.launcher.files

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.ensureActive
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
    var regions by remember(file) { mutableStateOf<ImageTextRegions.Result?>(null) }
    var imagePreview by remember(file) { mutableStateOf<Bitmap?>(null) }
    var backgroundStyle by remember(file) { mutableStateOf(ImageOverlayPlan.Background.MATCH_FLAT) }
    var foregroundStyle by remember(file) { mutableStateOf(ImageOverlayPlan.Foreground.AUTO) }
    var fontWeight by remember(file) { mutableStateOf(ImageOverlayPlan.Weight.NORMAL) }
    var originalPreview by remember(file) { mutableStateOf<Bitmap?>(null) }
    var showOriginal by remember(file) { mutableStateOf(false) }
    var regionIndex by remember(file) { mutableStateOf(0) }
    var regionEdits by remember(file) { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var script by remember(file) { mutableStateOf(ImageTranslationInput.Script.LATIN) }
    var rotation by remember(file) { mutableStateOf(ImageTranslationInput.Rotation.NONE) }
    var busy by remember(file) { mutableStateOf(true) }
    var identity by remember(file) { mutableStateOf<ImageDraftStore.Identity?>(null) }
    val draftDirectory = File(context.noBackupFilesDir, "image-translation-drafts")
    var message by remember(file) { mutableStateOf("") }

    fun allowed(): Boolean = GameplayBudget.allowsOptionalAnalysis(context)

    fun clearRegions() {
        imagePreview = null
        regions = null
        regionEdits = emptyMap()
        regionIndex = 0
    }

    fun draftAction(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = ""
        scope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "초안 작업 실패" }
            finally { busy = false }
        }
    }

    LaunchedEffect(file) {
        try {
            identity = withContext(Dispatchers.IO) {
                check(allowed())
                ImageDraftStore.identity(file)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "이미지 확인 실패. 다시 열어 주세요." }
        finally { busy = false }
    }

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
                        TextButton(enabled = !busy && option != script, onClick = { clearRegions(); script = option }) {
                            Text(option.label)
                        }
                    }
                    Text("인식 회전 각도: ${rotation.degrees}°")
                    TextButton(enabled = !busy, onClick = {
                        val options = ImageTranslationInput.Rotation.values()
                        clearRegions()
                        rotation = options[(rotation.ordinal + 1) % options.size]
                    }) { Text("시계 방향 90° 회전") }
                    Text("언어·회전 변경 시 영역 편집이 초기화됩니다. 다시 인식하면 현재 텍스트와 번역 초안도 교체됩니다. 먼저 초안을 저장하세요.")
                    Text("Google ML Kit가 기기에서 이미지 문자를 인식합니다. SDK의 사용량·성능 통계가 Google로 전송될 수 있습니다. 이미지나 인식 결과는 ML Kit 서버로 전송되지 않습니다.")
                    TextButton(enabled = !busy && identity != null, onClick = {
                        val expected = requireNotNull(identity)
                        val selectedScript = script
                        val selectedRotation = rotation
                        busy = true
                        message = ""
                        scope.launch {
                            try {
                                val recognized = withContext(Dispatchers.IO) {
                                    require(ImageDraftStore.identity(file) == expected) { "원본 이미지가 변경됐습니다." }
                                    val result = ImageOcrRuntime.recognize(file, selectedScript, selectedRotation, ::allowed)
                                    require(ImageDraftStore.identity(file) == expected) { "인식 중 원본 이미지가 변경됐습니다." }
                                    result
                                }
                                source = recognized.text
                                translated = ""
                                imagePreview = null
                                regions = recognized
                                regionIndex = 0
                                regionEdits = emptyMap()
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
                        if (source != value) {
                            imagePreview = null
                            regions = null
                            regionEdits = emptyMap()
                            regionIndex = 0
                        }
                        source = value
                        translated = ""
                    }
                }, enabled = !busy, label = { Text("추출한 글자 · 수정 가능") }, maxLines = 8)
                regions?.let { result ->
                    Text("글자 영역 ${result.regions.size}개 · 인식 이미지 ${result.width} × ${result.height}px")
                    if (result.partial) Text("일부 영역을 제외했습니다. 회전 인식은 글자 추출만 지원합니다.")
                    result.regions.getOrNull(regionIndex)?.let { region ->
                        Text("영역 ${region.id}/${result.regions.size} · 좌표 (${region.box.left}, ${region.box.top}) – (${region.box.right}, ${region.box.bottom})")
                        Text(region.text)
                        OutlinedTextField(regionEdits[region.id].orEmpty(), { value ->
                            if (value.length <= 1000 && value.none(Char::isISOControl)) {
                                imagePreview = null
                                regionEdits = if (value.isEmpty()) regionEdits - region.id
                                    else regionEdits + (region.id to value)
                            }
                        }, enabled = !busy, label = { Text("이 영역의 한국어 번역") }, maxLines = 3)
                        TextButton(enabled = !busy && regionIndex > 0, onClick = { regionIndex-- }) { Text("이전 영역") }
                        TextButton(enabled = !busy && regionIndex + 1 < result.regions.size, onClick = { regionIndex++ }) { Text("다음 영역") }
                        TextButton(enabled = !busy, onClick = {
                            try {
                                check(allowed())
                                AiHostBridge.requireCompatible()
                                val prompt = ImageTranslationInput.prompt(region.text)
                                context.startActivity(Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, prompt), "영역 번역 요청"))
                            } catch (e: Exception) { message = e.message ?: "공유 실패" }
                        }) { Text("선택 영역 번역 요청") }
                        Text("영역별 번역 ${regionEdits.values.count { it.isNotBlank() }}/${result.regions.size}개 · 초안 저장 가능")
                        TextButton(enabled = !busy && regionEdits.isNotEmpty(), onClick = {
                            try {
                                translated = ImageTextRegions.summary(result, regionEdits)
                                message = "입력한 영역의 번역만 전체 번역문으로 옮겼습니다. 위치 정보는 초안 저장 버튼으로 별도 보관할 수 있습니다."
                            } catch (e: Exception) { message = e.message ?: "영역 번역 취합 실패" }
                        }) { Text("입력한 영역 번역 취합 (전체 번역문 교체)") }
                        Text("최대 8Mi 픽셀의 원본 해상도 PNG 복사본을 생성합니다. 단색 배경을 추정하거나 검은 배경으로 덮습니다. 복잡한 배경 복원, 원본 압축 형식·메타데이터 보존은 지원하지 않습니다.")
                        Text("배경 방식: " + if (backgroundStyle == ImageOverlayPlan.Background.MATCH_FLAT) "단색 추정" else "검은 배경")
                        TextButton(enabled = !busy, onClick = {
                            imagePreview = null
                            backgroundStyle = if (backgroundStyle == ImageOverlayPlan.Background.MATCH_FLAT)
                                ImageOverlayPlan.Background.BLACK else ImageOverlayPlan.Background.MATCH_FLAT
                        }) { Text("배경 방식 변경") }
                        Text("글자 색: " + foregroundStyle.label + " · 굵기: " + fontWeight.label)
                        TextButton(enabled = !busy, onClick = {
                            imagePreview = null
                            val choices = ImageOverlayPlan.Foreground.values()
                            foregroundStyle = choices[(foregroundStyle.ordinal + 1) % choices.size]
                        }) { Text("글자 색 변경") }
                        TextButton(enabled = !busy, onClick = {
                            imagePreview = null
                            fontWeight = if (fontWeight == ImageOverlayPlan.Weight.NORMAL)
                                ImageOverlayPlan.Weight.BOLD else ImageOverlayPlan.Weight.NORMAL
                        }) { Text("글자 굵기 변경") }
                        TextButton(enabled = !busy && identity != null && regionEdits.isNotEmpty(), onClick = {
                            val expected = requireNotNull(identity)
                            val edits = regionEdits.toMap()
                            val style = ImageOverlayPlan.Appearance(backgroundStyle, foregroundStyle, fontWeight)
                            imagePreview = null
                            originalPreview = null
                            showOriginal = false
                            draftAction {
                                imagePreview = withContext(Dispatchers.IO) {
                                    ImageOverlayRenderer.render(file, expected, result, edits, style) {
                                        ensureActive()
                                        check(allowed()) { "게임 또는 메모리 상태로 이미지 작업을 중단했습니다." }
                                    }
                                }
                                message = "원본 해상도 번역 이미지 미리보기입니다. 글자와 배경을 확인하세요."
                            }
                        }) { Text("번역 이미지 미리보기 생성") }
                        imagePreview?.let { bitmap ->
                            val shown = if (showOriginal) originalPreview ?: bitmap else bitmap
                            val display = remember(shown) { shown.asImageBitmap() }
                            Text(if (showOriginal) "원본 이미지 (축소 보기)" else "번역 이미지")
                            Image(bitmap = display, contentDescription = if (showOriginal) "원본 이미지" else "번역 이미지 미리보기",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp))
                            TextButton(enabled = !busy && identity != null, onClick = {
                                if (showOriginal) showOriginal = false
                                else if (originalPreview != null) showOriginal = true
                                else {
                                    val expected = requireNotNull(identity)
                                    draftAction {
                                        originalPreview = withContext(Dispatchers.IO) {
                                            ImageOverlayRenderer.originalPreview(file, expected) {
                                                ensureActive()
                                                check(allowed())
                                            }
                                        }
                                        showOriginal = true
                                    }
                                }
                            }) { Text(if (showOriginal) "번역본 보기" else "원본과 비교") }
                            TextButton(enabled = !busy && identity != null && !showOriginal, onClick = {
                                val expected = requireNotNull(identity)
                                draftAction {
                                    val saved = withContext(Dispatchers.IO) {
                                        ImageOverlayRenderer.saveCopy(file, expected, bitmap) {
                                            ensureActive()
                                            check(allowed()) { "게임 또는 메모리 상태로 이미지 저장을 중단했습니다." }
                                        }
                                    }
                                    message = "새 PNG 복사본 저장: ${saved.name}. 원본은 수정하지 않았습니다."
                                }
                            }) { Text("검토한 이미지 PNG 복사본 저장") }
                            TextButton(enabled = !busy, onClick = { imagePreview = null; originalPreview = null; showOriginal = false }) { Text("미리보기 닫기") }
                        }
                    }
                }
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
                Text("초안을 저장하면 원문과 번역문, 영역 위치·번역, 언어·회전 설정이 앱 전용 저장소에 보관됩니다. 자동 전송되지 않습니다. 민감한 텍스트는 저장하지 마세요.")
                TextButton(enabled = !busy && identity != null && source.isNotBlank(), onClick = {
                    val expected = requireNotNull(identity)
                    val savedRegions = regions?.takeIf { rotation == ImageTranslationInput.Rotation.NONE && it.regions.isNotEmpty() }
                    val draft = ImageDraftStore.Draft(source, translated, script, rotation,
                        savedRegions, if (savedRegions == null) emptyMap() else regionEdits.toMap())
                    draftAction {
                        withContext(Dispatchers.IO) {
                            check(allowed())
                            ImageDraftStore.save(draftDirectory, file, expected, draft)
                        }
                        message = "현재 이미지 초안을 저장했습니다. 이전 저장본은 교체됩니다."
                    }
                }) { Text("이미지 번역 초안 저장 (이전 저장본 교체)") }
                TextButton(enabled = !busy && identity != null, onClick = {
                    val expected = requireNotNull(identity)
                    draftAction {
                        val draft = withContext(Dispatchers.IO) {
                            check(allowed())
                            require(ImageDraftStore.identity(file) == expected) { "원본 이미지가 변경됐습니다." }
                            ImageDraftStore.load(draftDirectory, expected).also {
                                require(ImageDraftStore.identity(file) == expected) { "원본 이미지가 변경됐습니다." }
                            }
                        }
                        if (draft == null) message = "현재 이미지 버전에 저장된 초안이 없습니다."
                        else {
                            imagePreview = null
                            regions = draft.regions
                            regionEdits = draft.edits
                            regionIndex = 0
                            source = draft.source
                            translated = draft.translated
                            script = draft.script
                            rotation = draft.rotation
                            message = "저장한 초안을 불러왔습니다."
                        }
                    }
                }) { Text("저장 초안 불러오기 (현재 편집 내용 교체)") }
                if (message.isNotEmpty()) Text(message)
                Text("닫기 전에 초안을 직접 저장하세요. 영역별 위치도 초안에 저장됩니다. 미리보기는 다시 생성하세요. 실시간 화면 번역은 지원하지 않습니다.")
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } },
    )
}
