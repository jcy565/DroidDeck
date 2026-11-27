package com.droiddeck.launcher.files

import androidx.compose.foundation.shape.CircleShape
import com.droiddeck.launcher.ui.focusGlide
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    var patchReport by remember(file) { mutableStateOf<ImagePatchImport.Report?>(null) }
    var patchMessage by remember(file) { mutableStateOf("") }
    var importPending by remember(file) { mutableStateOf(false) }
    val backupStorage = File(context.noBackupFilesDir, "image-patch-backups")
    var backup by remember(file) { mutableStateOf<ImagePatchBackup.Backup?>(null) }
    var transactionDiagnostic by remember(file) { mutableStateOf<ImagePatchTransaction.Diagnostic?>(null) }
    var probeResult by remember(file) { mutableStateOf("") }

    fun checkPatchImage(bytes: ByteArray, source: ImagePatchArchive.Source, checkpoint: () -> Unit = {}) {
        checkpoint()
        val extension = File(source.relative).extension.lowercase(Locale.ROOT)
        if (extension == "png") ImagePatchPngCheck.validate(bytes, source.width, source.height, checkpoint)
        val bounds = ImageAssetDecoder.bounds(bytes, extension)
        require(bounds.width == source.width && bounds.height == source.height)
        checkpoint()
    }

    fun backupTask(action: suspend () -> Unit) {
        if (busy || importPending) return
        busy = true
        patchMessage = ""
        scope.launch {
            try { action() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { patchMessage = e.message ?: "원본 백업 작업 실패" }
            finally { busy = false }
        }
    }
    val importPatch = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importPending = false
        if (uri != null && !busy && patchReport == null) {
            busy = true
            patchMessage = ""
            scope.launch {
                try {
                    patchReport = withContext(Dispatchers.IO) {
                        val coroutine = kotlinx.coroutines.currentCoroutineContext()
                        fun checkpoint() {
                            coroutine.ensureActive()
                            if (!GameplayBudget.allowsOptionalAnalysis(context))
                                throw kotlinx.coroutines.CancellationException("게임 또는 메모리 상태로 검사 중단")
                        }
                        requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                            ImagePatchImport.copyAndInspect(file, File(context.cacheDir, "image-patch-imports"), input,
                                verify = { bytes, source -> checkPatchImage(bytes, source, ::checkpoint) },
                                checkpoint = ::checkpoint)
                        }
                    }
                    patchMessage = "읽기 전용 검사 완료. 게임 파일은 변경하지 않았습니다."
                } catch (e: kotlinx.coroutines.CancellationException) {
                    patchMessage = "패치 검사를 중단했습니다."
                    throw e
                } catch (e: Exception) { patchMessage = e.message ?: "ZIP 패치 검사 실패" }
                finally { busy = false }
            }
        }
    }
    LaunchedEffect(file.absolutePath) {
        attrs = withContext(Dispatchers.IO) { readFileAttrs(file) }
    }

    OutlinedAlertDialog(
        onDismissRequest = { if (!busy && !importPending) onDismiss() },
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
                    Text("이 폴더를 게임 루트로 선택해 DroidDeck 이미지 ZIP 패치를 검사합니다. 출처가 확실한 파일만 선택하세요. 적용 기능은 아직 제공하지 않습니다.")
                    TextButton(enabled = !busy && !importPending && patchReport == null, onClick = {
                        try {
                            importPending = true
                            importPatch.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
                        } catch (e: Exception) {
                            importPending = false
                            patchMessage = e.message ?: "ZIP 선택 실패"
                        }
                    }) { Text("이미지 ZIP 패치 불러오기 및 검사") }
                    Text("원본 백업은 게임 폴더당 하나를 별도 앱 내부 저장소에 보관합니다. 기존 백업은 덮어쓰지 않습니다.")
                    TextButton(enabled = !busy && !importPending, onClick = {
                        backupTask {
                            backup = null
                            transactionDiagnostic = null
                            backup = withContext(Dispatchers.IO) {
                                check(GameplayBudget.allowsOptionalAnalysis(context))
                                ImagePatchBackup.load(backupStorage, file) {
                                    ensureActive()
                                    check(GameplayBudget.allowsOptionalAnalysis(context))
                                }
                            }
                            patchMessage = if (backup == null) "저장된 원본 백업 없음" else "원본 백업 무결성 검증 완료"
                        }
                    }) { Text("저장된 원본 백업 확인") }
                    Text("게임 폴더는 읽기 전용으로 검사하고, 파일 교체 시험은 앱 내부 임시 파일만 사용합니다. 게임 폴더의 쓰기 호환성을 보장하지 않습니다.")
                    TextButton(enabled = !busy && !importPending, onClick = {
                        backupTask {
                            probeResult = ""
                            val readResult = withContext(Dispatchers.IO) {
                                val coroutine = kotlinx.coroutines.currentCoroutineContext()
                                ImagePatchStorageCheck.inspect(file) {
                                    coroutine.ensureActive()
                                    if (!GameplayBudget.allowsOptionalAnalysis(context))
                                        throw kotlinx.coroutines.CancellationException("게임 또는 메모리 상태 변경")
                                }
                            }
                            val label = when (readResult) {
                                ImagePatchStorageCheck.Result.READ_CAPABLE -> "게임 폴더 안전한 읽기 경로 확인"
                                ImagePatchStorageCheck.Result.UNSAFE_ROOT -> "게임 폴더 경로 불일치 또는 심볼릭 링크"
                                ImagePatchStorageCheck.Result.NO_SECURE_STREAM -> "게임 저장소 안전한 디렉터리 핸들 미지원"
                                ImagePatchStorageCheck.Result.NO_POSIX_IDENTITY -> "게임 저장소 POSIX 파일 식별 정보 미지원"
                                ImagePatchStorageCheck.Result.READ_FAILED -> "게임 저장소 읽기 실패"
                            }
                            probeResult = label
                            try {
                                withContext(Dispatchers.IO) {
                                    val coroutine = kotlinx.coroutines.currentCoroutineContext()
                                    ImagePatchTransaction.probe(file, File(context.cacheDir, "image-transaction-probe")) {
                                        coroutine.ensureActive()
                                        if (!GameplayBudget.allowsOptionalAnalysis(context))
                                            throw kotlinx.coroutines.CancellationException("게임 또는 메모리 상태 변경")
                                    }
                                }
                                probeResult = "$label\n앱 내부 임시 파일 교체·복원 성공 (게임 저장소 쓰기 미검증)"
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                            catch (e: Exception) {
                                probeResult = "$label\n앱 내부 교체 시험 실패 또는 미지원: " + (e.message ?: e.javaClass.simpleName)
                            }
                        }
                    }) { Text("안전한 파일 교체 호환성 시험") }
                    if (probeResult.isNotEmpty()) Text(probeResult)
                    TextButton(enabled = !busy && !importPending && backup != null, onClick = {
                        backupTask {
                            transactionDiagnostic = null
                            transactionDiagnostic = withContext(Dispatchers.IO) {
                                val coroutine = kotlinx.coroutines.currentCoroutineContext()
                                ImagePatchTransaction.diagnose(backupStorage, file) {
                                    coroutine.ensureActive()
                                    check(GameplayBudget.allowsOptionalAnalysis(context))
                                }
                            }
                        }
                    }) { Text("패치 작업 기록 및 현재 파일 검사") }
                    transactionDiagnostic?.let { diagnostic ->
                        val label = when (diagnostic.journal.phase) {
                            ImagePatchTransaction.Phase.PREPARED -> "백업 준비"
                            ImagePatchTransaction.Phase.APPLYING -> "적용 중단 가능성 · 복구 필요"
                            ImagePatchTransaction.Phase.APPLIED -> "적용 기록 있음"
                            ImagePatchTransaction.Phase.RESTORED -> "복원 기록 있음"
                        }
                        Text("작업 기록: $label · 교체 시도 ${diagnostic.journal.attempted}개")
                        val assessment = when (diagnostic.assessment) {
                            ImagePatchReview.Assessment.ORIGINAL_MATCH -> "현재 파일이 백업 원본과 모두 일치"
                            ImagePatchReview.Assessment.APPLIED_MATCH -> "현재 파일이 번역본과 모두 일치"
                            ImagePatchReview.Assessment.INTERRUPTED -> "적용 또는 복원 작업이 중단됐을 수 있음 · 검토 필요"
                            ImagePatchReview.Assessment.OUTSIDE_JOURNAL -> "교체 시도 기록 밖에서 변경된 번역본 발견 · 자동 복구 금지"
                            ImagePatchReview.Assessment.BLOCKED -> "파일 충돌 또는 읽기 오류 · 자동 복구 금지"
                        }
                        Text("일치 상태: $assessment")
                        diagnostic.assets.forEach { asset ->
                            val state = when (asset.state) {
                                ImagePatchTransaction.AssetState.ORIGINAL -> "백업 원본과 일치"
                                ImagePatchTransaction.AssetState.TRANSLATED -> "패치 번역본과 일치"
                                ImagePatchTransaction.AssetState.CONFLICT -> "다른 버전 또는 변경된 파일"
                                ImagePatchTransaction.AssetState.UNAVAILABLE -> "안전한 읽기 미지원 또는 파일 오류"
                            }
                            Text("${asset.relative} · $state")
                        }
                        Text("읽기 전용 검사 결과입니다. 실제 패치 적용·복구는 아직 비활성 상태입니다.")
                    }
                    backup?.let { saved ->
                        Text("원본 백업 ${saved.rows.size}개 파일 · 패치 SHA-256 ${saved.patchDigest.take(12)}…")
                        saved.rows.forEach { Text(it.source.relative) }
                        Text("백업 파일이 검증됐습니다. 실제 게임 복구 기능은 아직 제공하지 않습니다.")
                    }
                    if (patchMessage.isNotEmpty()) Text(patchMessage)
                    patchReport?.let { report ->
                        Text("원본 일치 ${report.entries.count { it.state == ImagePatchImport.State.READY }} / ${report.entries.size}개 · 검사 시점 기준")
                        report.entries.forEach { entry ->
                            val label = when (entry.state) {
                                ImagePatchImport.State.READY -> "원본 일치"
                                ImagePatchImport.State.ALREADY_APPLIED -> "번역본과 동일"
                                ImagePatchImport.State.MISSING -> "파일 없음"
                                ImagePatchImport.State.UNSAFE -> "지원하지 않는 경로"
                                ImagePatchImport.State.MISMATCH -> "원본 버전 불일치"
                                ImagePatchImport.State.INVALID_OUTPUT -> "패치 이미지 형식 또는 크기 불일치"
                                ImagePatchImport.State.READ_FAILED -> "읽기 또는 원본 형식 검사 실패"
                            }
                            Text("${entry.row.source.relative} · $label")
                        }
                        TextButton(enabled = !busy && backup == null && report.entries.all { it.state == ImagePatchImport.State.READY }, onClick = {
                            backupTask {
                                backup = withContext(Dispatchers.IO) {
                                    val coroutine = kotlinx.coroutines.currentCoroutineContext()
                                    fun checkpoint() {
                                        coroutine.ensureActive()
                                        if (!GameplayBudget.allowsOptionalAnalysis(context))
                                            throw kotlinx.coroutines.CancellationException("게임 또는 메모리 상태로 백업 중단")
                                    }
                                    ImagePatchBackup.create(backupStorage, file, report,
                                        verify = { bytes, source -> checkPatchImage(bytes, source, ::checkpoint) },
                                        checkpoint = ::checkpoint)
                                }
                                patchMessage = "원본 백업 저장 및 검증 완료. 게임 파일은 변경하지 않았습니다."
                            }
                        }) { Text("패치 대상 원본 전체 백업") }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { java.nio.file.Files.deleteIfExists(report.file.toPath()) }
                                    patchReport = null
                                    patchMessage = "임시 검사 파일 삭제 완료"
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { patchMessage = e.message ?: "임시 파일 삭제 실패" }
                                finally { busy = false }
                            }
                        }) { Text("검사 결과 닫기 및 임시 ZIP 삭제") }
                    }
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
        confirmButton = { TextButton(enabled = !busy && !importPending, onClick = onDismiss, modifier = Modifier.focusGlide(CircleShape)) { Text(stringResource(R.string.fm_done)) } },
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
