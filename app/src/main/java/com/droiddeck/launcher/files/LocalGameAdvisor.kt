package com.droiddeck.launcher.files

import java.nio.ByteBuffer

internal object LocalGameAdvisor {
    fun decode(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().also {
            require(it.none { c -> c.isISOControl() && c !in "\t\r\n" }) { "UTF-8 텍스트 파일만 지원합니다." }
        }

    fun replacement(target: String, source: String?, input: java.io.InputStream): String {
        require(target.substringAfterLast('.').lowercase() in GamePatchStore.extensions)
        val payload = GamePatchStore.readLimited(input)
        if (target.equals(source, ignoreCase = true)) return decode(payload)
        require(source?.endsWith(".zip", ignoreCase = true) == true) { "이름이 같은 텍스트 파일이나 단일 파일 ZIP을 선택하세요." }
        return java.util.zip.ZipInputStream(payload.inputStream()).use { zip ->
            val entry = zip.nextEntry ?: error("빈 ZIP 파일입니다.")
            require(!entry.isDirectory && entry.name == target && '/' !in entry.name && '\\' !in entry.name) {
                "ZIP 최상위에 대상과 이름이 같은 파일 하나만 지원합니다."
            }
            val text = decode(GamePatchStore.readLimited(zip))
            require(zip.nextEntry == null) { "여러 파일을 포함한 ZIP은 지원하지 않습니다." }
            text
        }
    }

    private enum class LogSignal(val marker: Regex, val code: String, val evidence: String) {
        GPU_MEMORY(Regex("\\bVK_ERROR_OUT_OF_DEVICE_MEMORY\\b", RegexOption.IGNORE_CASE),
            "VK_ERROR_OUT_OF_DEVICE_MEMORY", "GPU 메모리 할당 실패가 기록됐습니다."),
        SYSTEM_MEMORY(Regex("\\breason\\s*[:=]?\\s+LOW_MEMORY\\b|\\breason\\s*[:=]\\s*LOW_MEMORY\\b", RegexOption.IGNORE_CASE),
            "LOW_MEMORY", "Android의 메모리 부족 종료 기록입니다."),
        VULKAN_LOST(Regex("\\bVK_ERROR_DEVICE_LOST\\b", RegexOption.IGNORE_CASE),
            "VK_ERROR_DEVICE_LOST", "Vulkan 장치 연결 손실이 기록됐습니다."),
        DIRECTX_LOST(Regex("\\bDXGI_ERROR_DEVICE_REMOVED\\b", RegexOption.IGNORE_CASE),
            "DXGI_ERROR_DEVICE_REMOVED", "DirectX 장치 손실이 기록됐습니다.")
    }

    fun diagnose(log: String): String {
        val observed = LogSignal.values().filter { it.marker.containsMatchIn(log) }
        if (observed.isEmpty()) return "알려진 오류 신호가 없습니다. 검은 화면의 원인은 이 로그만으로 확정하기 어렵습니다."
        val memory = LogSignal.GPU_MEMORY in observed || LogSignal.SYSTEM_MEMORY in observed
        val device = LogSignal.VULKAN_LOST in observed || LogSignal.DIRECTX_LOST in observed
        return buildString {
            append("관측된 오류 신호\n")
            observed.forEach { append(it.code).append(" · ").append(it.evidence).append('\n') }
            append("\n다음 확인 순서 (자동 변경 없음)\n")
            var step = 1
            fun next(text: String) { append(step++).append(". ").append(text).append('\n') }
            next("오류 기록이 이번 게임 종료와 같은 실행 세션인지 확인하세요.")
            if (memory) {
                next("게임을 종료하고 메모리 상태를 확인하세요. Android 메모리와 GPU 메모리는 구분합니다.")
                next("게임의 INI/CFG를 열고 ‘현재 설정에서 최적화 후보 찾기’를 선택하세요. 지원되는 경우 720p 또는 900p 해상도 후보 하나만 미리보기 하세요.")
                next("원본을 백업한 후 한 가지 설정만 적용하고 재실행하세요. 실제 게임 내부 해상도와 출력 해상도를 따로 확인하세요.")
            }
            if (device) {
                next("드라이버 버전 및 DXVK/VKD3D 사용 여부와 최초 장치 손실 시점을 확인하세요. 장치 손실만으로 해상도나 드라이버가 원인이라고 단정하지 않습니다.")
            }
            next("같은 장면에서 실행 성공 여부와 FPS·P95 프레임타임을 비교하세요. 악화되면 기존 백업을 검토하세요.")
            append("이 기록은 원인 확정이나 성능 향상 보장이 아닙니다.")
        }
    }

    private const val UNREAL = "/Script/Engine.GameUserSettings"

    data class Preset(val title: String, val description: String, val preview: GameConfigPreview.Preview)

    /** Offer only supported, non-noop reductions against the exact current file. */
    fun presets(original: String): List<Preset> {
        val options = listOf("900p 균형" to "900p 해상도", "720p" to "720p 해상도", "배터리 30 FPS" to "30 FPS 제한")
        return options.mapNotNull { (command, title) ->
            val plan = try { optimize(original, command) }
                catch (_: IllegalArgumentException) { return@mapNotNull null }
            if (plan.changes.isEmpty()) return@mapNotNull null
            val description = if (command.contains("30")) {
                val before = plan.changes.single().before
                "프레임 제한: " + (if (before.toDoubleOrNull() == 0.0) "무제한" else before) + " → 30 FPS"
            } else {
                val height = if (command.contains("900")) 900 else 720
                val width = height * 16 / 9
                val single = plan.changes.firstOrNull { it.key == "Resolution" }
                val old = if (single != null) {
                    val parts = single.before.split('x')
                    parts[0].toLong() * parts[1].toLong()
                } else {
                    val w = plan.changes.firstOrNull { it.key == "ResolutionSizeX" }?.before?.toLong() ?: width.toLong()
                    val h = plan.changes.firstOrNull { it.key == "ResolutionSizeY" }?.before?.toLong() ?: height.toLong()
                    w * h
                }
                val reduction = (1.0 - width.toLong() * height / old.toDouble()) * 100
                java.lang.String.format(java.util.Locale.ROOT,
                    "%dx%d · 설정상 픽셀 수 %.1f%% 감소 (FPS 예측 아님)", width, height, reduction)
            }
            Preset(title, description, plan)
        }
    }

    fun optimize(original: String, command: String = ""): GameConfigPreview.Preview {
        val unreal = original.lineSequence().any { it.trim() == "[$UNREAL]" }
        if (command.contains("30") || command.contains("배터리")) {
            require(unreal) { "이 파일에서 지원하는 프레임 제한 설정을 찾지 못했습니다." }
            val plan = GameConfigPreview.preview(original, UNREAL, mapOf("FrameRateLimit" to "30.000000"))
            val value = plan.changes.singleOrNull()?.before?.toDoubleOrNull() ?: if (plan.changes.isEmpty()) 30.0 else Double.NaN
            require(value == 0.0 || value in 30.0..1000.0) { "현재 프레임 제한을 높이거나 해석할 수 없는 값은 변경하지 않습니다." }
            return plan
        }
        val height = if ("900" in command || "균형" in command) 900 else 720
        val width = height * 16 / 9
        val plan: GameConfigPreview.Preview
        val w: Int?
        val h: Int?
        if (unreal) {
            plan = GameConfigPreview.preview(original, UNREAL, mapOf("ResolutionSizeX" to width.toString(), "ResolutionSizeY" to height.toString()))
            fun previous(key: String, same: Int) =
                plan.changes.firstOrNull { it.key == key }?.let { it.before.toIntOrNull() ?: -1 } ?: same
            w = previous("ResolutionSizeX", width)
            h = previous("ResolutionSizeY", height)
        } else {
            plan = GameConfigPreview.preview(original, "Render", mapOf("Resolution" to "${width}x$height"))
            val previous = plan.changes.singleOrNull()?.before ?: return plan
            val size = Regex("([0-9]{2,5})x([0-9]{2,5})").matchEntire(previous)
            w = size?.groupValues?.get(1)?.toInt()
            h = size?.groupValues?.get(2)?.toInt()
        }
        require(w != null && h != null && w >= width && h >= height && w.toLong() * 9 == h.toLong() * 16) {
            "현재 해상도와 화면비에는 이 제안을 적용할 수 없습니다."
        }
        return plan
    }
}
