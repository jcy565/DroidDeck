package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.droiddeck.launcher.core.FexPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Global preference comparison only; per-game overrides can differ. */
@Composable
internal fun RuntimeFexPreviewPanel(profileKey: String?) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var options by remember(context, profileKey) { mutableStateOf<List<RuntimeSettingsPreview.Change>?>(null) }
    var selection by remember(context, profileKey) { mutableStateOf<RuntimeSettingsPreview.FexSelection?>(null) }
    var busy by remember(context) { mutableStateOf(false) }
    var error by remember(context) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("FEX 프리셋 미리보기")
        Text("전체 기본 프리셋입니다. 게임별 재정의가 우선할 수 있으며 실제 성능을 보장하지 않습니다.")
        Button(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = {
            if (!busy) {
                busy = true
                options = null
                selection = null
                error = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
                            val snapshot = RuntimeSettingsPreview.inspect(context)
                            val config = com.droiddeck.launcher.session.GameEnvironmentStore.read(context)
                            val selected = RuntimeSettingsPreview.fexSelection(snapshot.fexPreset, config, profileKey)
                            val changes = RuntimeSettingsPreview.fexAlternatives(snapshot)
                            check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
                            selected to changes
                        }
                        selection = result.first
                        options = result.second
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        error = "FEX 설정을 읽지 못했습니다. 게임 종료 후 다시 확인해 주세요."
                    } finally {
                        busy = false
                    }
                }
            }
        }) { Text(if (busy) "조회 중" else "FEX 프리셋 비교") }
        error?.let { Text(it) }
        selection?.let { selected ->
            val override = selected.gameOverride
            val label = when {
                !selected.profileKnown -> "게임 프로필 미확인"
                override == null -> "게임별 재정의 없음"
                override == com.droiddeck.launcher.core.GameEnvironment.CUSTOM_FEX_PRESET -> "사용자 지정 환경"
                else -> FexPreset.all.firstOrNull { it.id == override }?.let { stringResource(it.label) } ?: "알 수 없음"
            }
            Text("게임별 저장 설정: " + label)
            if (selected.sharedOverride) Text("공통 환경 변수에 FEX 재정의가 있습니다.")
        }
        options?.let { changes ->
            changes.firstOrNull()?.let { first ->
                val current = FexPreset.all.firstOrNull { it.id == first.before }
                val label = if (current == null) "알 수 없는 프리셋" else stringResource(current.label)
                Text("현재 기본 설정: " + label)
            }
            changes.forEach { change ->
                val preset = FexPreset.all.firstOrNull { it.id == change.proposed }
                if (preset != null) {
                    Text(stringResource(preset.label) + if (change.before == change.proposed) " (현재)" else "")
                    Text(stringResource(preset.detail))
                }
            }
            Text("조회 전용이며 FEX 설치, 변경 또는 자동 적용은 수행하지 않습니다.")
        }
    }
}
