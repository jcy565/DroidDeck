package com.droiddeck.launcher.files

import androidx.compose.foundation.shape.CircleShape
import com.droiddeck.launcher.ui.focusGlide
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.droiddeck.launcher.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.droiddeck.launcher.compatibility.GameplayBudget
import kotlinx.coroutines.ensureActive
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@Composable
internal fun FilePropertiesDialog(
    file: File,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onTranslationFile: ((File) -> Unit)? = null,
    onTranslationQueue: ((TranslationWorklist.Queue) -> Unit)? = null,
    onImageQueue: ((ImageAssetDiscovery.Queue) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    var attrs by remember(file.absolutePath) { mutableStateOf<FileAttrState?>(null) }
    var busy by remember(file.absolutePath) { mutableStateOf(false) }
    var usage by remember(file) { mutableStateOf("") }
    var discovery by remember(file) { mutableStateOf<TranslationDiscovery.Report?>(null) }
    var discoveryMessage by remember(file) { mutableStateOf("") }
    var selectedTranslations by remember(file) { mutableStateOf(emptySet<String>()) }
    var imageReport by remember(file) { mutableStateOf<ImageAssetDiscovery.Report?>(null) }
    var imageMessage by remember(file) { mutableStateOf("") }
    var selectedImages by remember(file) { mutableStateOf(emptySet<String>()) }
    var examinedImages by remember(file) { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(file.absolutePath) {
        attrs = withContext(Dispatchers.IO) { readFileAttrs(file) }
    }

    OutlinedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fm_properties)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = file.name,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                PropertyLine(stringResource(R.string.fm_prop_location), file.parent ?: "-")
                PropertyLine(
                    stringResource(R.string.fm_prop_type),
                    if (file.isDirectory) stringResource(R.string.fm_type_folder)
                    else file.extension.uppercase().let { if (it.isBlank()) stringResource(R.string.fm_type_file) else stringResource(R.string.fm_type_ext_file, it) },
                )
                if (!file.isDirectory) PropertyLine(stringResource(R.string.fm_prop_size), FileOps.formatBytes(context, file.length()))
                PropertyLine(stringResource(R.string.fm_prop_modified), dateFormat.format(Date(file.lastModified())))

                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))

                if (file.isDirectory) {
                    TextButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val r = withContext(Dispatchers.IO) {
                                    check(GameplayBudget.allowsOptionalAnalysis(context)) { "게임 또는 메모리 상태 때문에 분석을 보류했습니다." }
                                    FolderUsage.inspect(file) {
                                        ensureActive()
                                        check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN)) { "게임 상태가 바뀌어 분석을 중단했습니다." }
                                    }
                                }
                                usage = buildString {
                                    append("파일 ${r.files}개 · ${FileOps.formatBytes(context, r.bytes)}")
                                    if (r.partial) append(" (부분 집계)")
                                    append("\n캐시·임시 파일 후보 ${FileOps.formatBytes(context, r.temporaryBytes)}")
                                    if (r.candidates.isNotEmpty()) append("\n이름과 크기가 같은 후보\n" + r.candidates.joinToString("\n"))
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) { usage = e.message ?: "폴더 분석 실패" }
                            finally { busy = false }
                        }
                    }) { Text("폴더 사용량 분석") }
                    if (usage.isNotEmpty()) Text(usage)
                    if (onImageQueue != null) {
                        fun scanImages(continueScan: Boolean) {
                            busy = true
                            imageReport = null
                            imageMessage = ""
                            selectedImages = emptySet()
                            if (!continueScan) examinedImages = emptySet()
                            val skip = examinedImages.toSet()
                            scope.launch {
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        check(GameplayBudget.allowsOptionalAnalysis(context))
                                        ImageAssetDiscovery.inspect(file, skip = skip, probe = { bytes, ext ->
                                            val bounds = ImageAssetDecoder.bounds(bytes, ext)
                                            ImageAssetDiscovery.Size(bounds.width, bounds.height)
                                        }) {
                                            ensureActive()
                                            check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN))
                                        }
                                    }
                                    examinedImages = skip + result.examinedPaths
                                    imageReport = result
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { imageMessage = e.message ?: "이미지 탐색 실패" }
                                finally { busy = false }
                            }
                        }
                        TextButton(enabled = !busy, onClick = { scanImages(false) }) { Text("이미지 한글화 대상 탐색") }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            imageMessage = ""
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) {
                                        check(GameplayBudget.allowsOptionalAnalysis(context))
                                        ImageQueueStore.load(File(context.noBackupFilesDir, "image-worklists"), file)
                                    }
                                    if (saved == null) imageMessage = "저장된 이미지 작업 목록이 없습니다."
                                    else onImageQueue(saved)
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { imageMessage = e.message ?: "작업 목록 복원 실패" }
                                finally { busy = false }
                            }
                        }) { Text("저장된 이미지 작업 목록 복원") }
                        if (imageMessage.isNotEmpty()) Text(imageMessage)
                        imageReport?.let { report ->
                            Text("이미지 후보 ${report.candidates.size}개 · 검사 ${report.examined}개 · 제외 ${report.rejected}개" +
                                if (report.partial) " (부분 탐색)" else "")
                            Text("파일 형식과 크기만 검사했습니다. 외국어 포함 여부는 편집기에서 확인하세요.")
                            Text("이번 페이지 선택은 다음 탐색 시 초기화됩니다. 선택한 이미지를 저장 목록에 추가한 뒤 다음 페이지를 탐색하세요. 목록은 폴더별 최대 20개입니다.")
                            TextButton(enabled = !busy && report.canContinue, onClick = { scanImages(true) }) { Text("다음 이미지 탐색 (이전 검사 제외)") }
                            TextButton(enabled = !busy && report.candidates.isNotEmpty(), onClick = {
                                selectedImages = if (selectedImages.size == report.candidates.size) emptySet()
                                    else report.candidates.map { it.relative }.toSet()
                            }) { Text("이미지 전체 선택 / 해제") }
                            report.candidates.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Checkbox(checked = item.relative in selectedImages,
                                        enabled = !busy, onCheckedChange = { checked ->
                                            selectedImages = if (checked) selectedImages + item.relative else selectedImages - item.relative
                                        })
                                    Text("${item.relative} · ${item.width} × ${item.height}px")
                                }
                            }
                            TextButton(enabled = !busy && selectedImages.isNotEmpty(), onClick = {
                                val incoming = ImageAssetDiscovery.Queue(file, report.candidates.filter { it.relative in selectedImages })
                                busy = true
                                imageMessage = ""
                                scope.launch {
                                    try {
                                        val merged = withContext(Dispatchers.IO) {
                                            check(GameplayBudget.allowsOptionalAnalysis(context))
                                            for (item in incoming.candidates) {
                                                ensureActive()
                                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                                ImageAssetDiscovery.open(incoming.root, item)
                                            }
                                            ensureActive()
                                            check(GameplayBudget.allowsOptionalAnalysis(context))
                                            ImageQueueStore.merge(File(context.noBackupFilesDir, "image-worklists"), incoming)
                                        }
                                        imageMessage = "저장 목록에 추가 완료 · 총 ${merged.candidates.size}개"
                                        selectedImages = emptySet()
                                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                    catch (e: Exception) { imageMessage = e.message ?: "이미지 작업 목록 추가 실패" }
                                    finally { busy = false }
                                }
                            }) { Text("선택한 이미지를 저장 목록에 추가") }
                            TextButton(enabled = !busy && selectedImages.isNotEmpty(), onClick = {
                                onImageQueue(ImageAssetDiscovery.Queue(file,
                                    report.candidates.filter { it.relative in selectedImages }))
                            }) { Text("선택한 이미지 ${selectedImages.size}개 작업 목록 열기") }
                        }
                    }
                    if (onTranslationFile != null) {
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            discovery = null
                            selectedTranslations = emptySet()
                            discoveryMessage = ""
                            scope.launch {
                                try {
                                    discovery = withContext(Dispatchers.IO) {
                                        check(GameplayBudget.allowsOptionalAnalysis(context)) { "게임 또는 메모리 상태 때문에 탐색을 보류했습니다." }
                                        TranslationDiscovery.inspect(file) {
                                            ensureActive()
                                            check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN)) { "게임 상태가 바뀌어 탐색을 중단했습니다." }
                                        }
                                    }
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { discoveryMessage = e.message ?: "번역 대상 탐색 실패" }
                                finally { busy = false }
                            }
                        }) { Text("한글 패치 대상 탐색") }
                        if (discoveryMessage.isNotEmpty()) Text(discoveryMessage)
                        discovery?.let { report ->
                            Text("지원 형식 후보 ${report.candidates.size}개 · 검사 ${report.examined}개 · 제외 ${report.rejected}개" +
                                if (report.partial) " (부분 탐색)" else "")
                            Text("CSV·TSV·JSON의 지원 구조만 확인했습니다. 실제 게임 번역 파일인지는 직접 확인하세요.")
                            if (onTranslationQueue != null && report.candidates.isNotEmpty()) {
                                TextButton(enabled = !busy, onClick = {
                                    selectedTranslations = if (selectedTranslations.size == report.candidates.size) emptySet()
                                        else report.candidates.map { it.relative }.toSet()
                                }) { Text("전체 선택 / 해제") }
                                TextButton(enabled = !busy && selectedTranslations.isNotEmpty(), onClick = {
                                    onTranslationQueue(TranslationWorklist.Queue(file,
                                        report.candidates.filter { it.relative in selectedTranslations }))
                                }) { Text("선택한 ${selectedTranslations.size}개 파일 작업 목록 열기") }
                            }
                            report.candidates.forEach { candidate ->
                                if (onTranslationQueue != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        androidx.compose.material3.Checkbox(
                                            checked = candidate.relative in selectedTranslations,
                                            enabled = !busy,
                                            onCheckedChange = { checked ->
                                                selectedTranslations = if (checked) selectedTranslations + candidate.relative
                                                    else selectedTranslations - candidate.relative
                                            })
                                        Text(candidate.relative)
                                    }
                                }
                                TextButton(enabled = !busy, onClick = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val selected = withContext(Dispatchers.IO) {
                                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                                val checked = TranslationDiscovery.open(file, candidate)
                                                check(GameplayBudget.allows(GameplayBudget.Work.STORAGE_SCAN))
                                                checked
                                            }
                                            onTranslationFile(selected)
                                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                        catch (e: Exception) { discoveryMessage = e.message ?: "파일 열기 실패" }
                                        finally { busy = false }
                                    }
                                }) { Text("${candidate.relative} · ${candidate.rows}문장") }
                            }
                        }
                    }
                }
                val state = attrs
                AttributeToggleRow(
                    label = stringResource(R.string.fm_read_only),
                    description = stringResource(R.string.fm_read_only_hint),
                    checked = state?.readOnly == true,
                    enabled = state != null && !busy,
                    onToggle = { want ->
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { setReadOnly(file, want) }
                            busy = false
                            if (ok) {
                                attrs = attrs?.copy(readOnly = want)
                                onChanged()
                            } else {
                                Toast.makeText(context, R.string.fm_read_only_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
                Spacer(Modifier.height(2.dp))
                val hiddenSupported = state?.hiddenSupported == true
                AttributeToggleRow(
                    label = stringResource(R.string.fm_hidden),
                    description = if (state != null && !hiddenSupported)
                        stringResource(R.string.fm_hidden_unsupported)
                    else stringResource(R.string.fm_hidden_hint),
                    checked = state?.hidden == true,
                    enabled = state != null && hiddenSupported && !busy,
                    onToggle = { want ->
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { setHidden(file, want) }
                            busy = false
                            if (ok) {
                                attrs = attrs?.copy(hidden = want)
                                onChanged()
                            } else {
                                attrs = attrs?.copy(hiddenSupported = false)
                                Toast.makeText(context, R.string.fm_hidden_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.fm_done)) } },
    )
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AttributeToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.focusGlide(),
        )
    }
}
