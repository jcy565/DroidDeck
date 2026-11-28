package com.droiddeck.launcher.ui

import android.os.Build
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.droiddeck.launcher.compatibility.GameplayBudget
import com.droiddeck.launcher.compatibility.OptimizationComparison
import com.droiddeck.launcher.compatibility.PerformanceRetestAdvice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun GamePerformanceHistoryPanel(gameId: String, enabled: Boolean = true) {
    key(gameId) {
        val context = LocalContext.current.applicationContext
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        var entries by remember { mutableStateOf<List<TrialDatabase.Entry>?>(null) }
        var before by remember { mutableStateOf<TrialDatabase.Entry?>(null) }
        var after by remember { mutableStateOf<TrialDatabase.Entry?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        Text("게임 성능 측정 이력")
        Text("이 기기에서 저장된 최근 검증 측정 최대 20건 · 게임 버전 및 내부 렌더 해상도는 기록되지 않습니다.")
        TextButton(enabled = enabled && !busy, onClick = {
            if (busy) return@TextButton
            busy = true
            entries = null
            before = null
            after = null
            error = null
            scope.launch {
                try {
                    entries = withContext(Dispatchers.IO) {
                        check(GameplayBudget.allowsOptionalAnalysis(context)) { "게임 종료 후 다시 조회하세요." }
                        val db = TrialDatabase(context)
                        try {
                            db.recent(gameId, Build.MODEL).also {
                                check(GameplayBudget.allowsOptionalAnalysis(context)) { "성능 이력 조회를 보류했습니다." }
                            }
                        } finally { db.close() }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: "성능 이력을 조회하지 못했습니다."
                } finally { busy = false }
            }
        }) { Text("성능 측정 기록 조회") }
        if (busy) Text("조회 중")
        error?.let { Text(it) }
        entries?.let { rows ->
            if (rows.isEmpty()) Text("검증된 성능 측정 기록이 없습니다. 게임 실행 중 10초 측정을 먼저 수행하세요.")
            val formatter = DateFormat.getDateTimeInstance()
            rows.forEach { row ->
                val s = row.sample
                Text(formatter.format(Date(row.recordedAt)) + " · " + s.scene)
                Text(String.format(Locale.ROOT, "%d×%d · %.1f FPS · P95 %.1f ms · %d 프레임",
                    s.outputWidth, s.outputHeight, s.fps, s.p95Ms, row.frames))
                Text(row.runtime)
                TextButton(enabled = enabled && after?.id != row.id, onClick = { before = row }) {
                    Text(if (before?.id == row.id) "변경 전으로 선택됨" else "변경 전으로 선택")
                }
                TextButton(enabled = enabled && before?.id != row.id, onClick = { after = row }) {
                    Text(if (after?.id == row.id) "변경 후로 선택됨" else "변경 후로 선택")
                }
            }
            val a = before
            if (a != null) {
                val candidates = remember(rows, a) {
                    PerformanceRetestAdvice.candidates(rows.map {
                        PerformanceRetestAdvice.Observation(it.id, it.sample, it.runtime)
                    }, a.id)
                }
                Text("선택한 기준 측정 대비 재시험 후보")
                Text("같은 게임·기기·장면 이름·출력 해상도에서 FPS 또는 P95가 개선되고 다른 지표가 악화되지 않은 최근 기록입니다.")
                if (candidates.isEmpty()) Text("조건을 충족하는 개선 기록이 없습니다. 같은 장면에서 다시 측정하세요.")
                candidates.forEach { candidate ->
                    val item = rows.single { it.id == candidate.observed.id }
                    Text(formatter.format(Date(item.recordedAt)) + " · " + item.sample.scene)
                    Text(item.runtime)
                    Text(OptimizationComparison.describe(candidate.comparison))
                    TextButton(enabled = enabled, onClick = {
                        if (item.id > a.id) after = item
                        else { before = item; after = a }
                    }) { Text("이 기록과 기준 측정 비교") }
                }
                Text("표시된 실행 환경은 재시험 참고용입니다. 이전 게임 설정 전체가 저장된 것은 아니며 자동 적용하지 않습니다.")
            }
            val b = after
            if (a != null && b != null) {
                Text("선택한 두 기록의 비교")
                Text(if (a.id >= b.id) "변경 전 기록은 변경 후보다 먼저 저장된 기록을 선택하세요."
                    else OptimizationComparison.describe(OptimizationComparison.compare(a.sample, b.sample)))
                Text("장면 이름은 사용자 입력이며 게임 버전·내부 설정·실제 조건 일치는 확인하지 않습니다.")
            }
            TextButton(enabled = enabled, onClick = { entries = null; before = null; after = null }) {
                Text("성능 이력 닫기")
            }
        }
    }
}
