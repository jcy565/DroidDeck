package com.droiddeck.launcher.compatibility

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.ui.GpuDriversState

/** Explicit inspection of the existing selector rows, not a download or apply action. */
@Composable
internal fun RuntimeDriverPreviewPanel(gpu: GpuDriversState) {
    var result by remember(gpu.linuxRows, gpu.androidRows, gpu.linuxSelected, gpu.androidSelected,
        gpu.busy, gpu.checking) { mutableStateOf<RuntimeSettingsPreview.DriverComparison?>(null) }
    var error by remember(gpu.busy, gpu.checking) { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("로컬 GPU 드라이버 비교")
        Button(modifier = Modifier.fillMaxWidth(), enabled = gpu.busy == null && !gpu.checking, onClick = {
            result = null
            error = null
            try {
                check(GameplayBudget.allows(GameplayBudget.Work.ANALYSIS))
                result = RuntimeSettingsPreview.compareLocalDrivers(gpu)
            } catch (_: Exception) {
                error = "게임 또는 드라이버 작업을 종료한 후 다시 확인해 주세요."
            }
        }) { Text("로컬 후보 확인") }
        error?.let { Text(it) }
        result?.let { comparison ->
            Text("조회 당시 선택값과 로컬 후보입니다. 실제 사용 중인 드라이버와 다를 수 있습니다.")
            fun selectedText(value: String) = value.ifEmpty { "기본값" }.take(256)
            Text("Linux 선택값: " + selectedText(comparison.linuxSelected))
            DriverOptions(comparison.linux)
            Text("Android 선택값: " + selectedText(comparison.androidSelected))
            DriverOptions(comparison.android)
            Text("읽기 전용입니다. 드라이버 설치, 전환 및 자동 복구는 수행하지 않습니다.")
        }
    }
}

@Composable
private fun DriverOptions(options: List<RuntimeSettingsPreview.DriverOption>) {
    if (options.isEmpty()) Text("명시적으로 선택 가능한 로컬 후보 없음")
    options.take(30).forEach { option ->
        val suffix = if (option.change.before == option.change.proposed) " (현재 선택)" else ""
        Text(option.name.take(100) + suffix)
        Text(option.change.proposed.take(256))
        if (option.detail.isNotBlank()) Text(option.detail.take(160))
    }
    if (options.size > 30) Text("전체 " + options.size + "개 중 30개 표시")
}
