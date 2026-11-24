package com.droiddeck.launcher.files

import java.nio.ByteBuffer

internal object LocalGameAdvisor {
    fun decode(bytes: ByteArray): String =
        Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().also {
            require(it.none { c -> c.isISOControl() && c !in "\t\r\n" }) { "UTF-8 텍스트 파일만 지원합니다." }
        }

    fun diagnose(log: String): String {
        val signals = listOf(
            "VK_ERROR_OUT_OF_DEVICE_MEMORY" to "GPU 메모리 할당 실패가 기록됐습니다. 같은 장면에서 텍스처 품질을 한 단계 낮춰 비교하세요.",
            "reason LOW_MEMORY" to "Android의 메모리 부족 종료 기록입니다. 기록 시각과 게임 종료 시각을 비교하세요.",
            "VK_ERROR_DEVICE_LOST" to "Vulkan 장치 연결 손실이 기록됐습니다. 드라이버 버전과 최초 오류 시각을 확인하세요.",
            "DXGI_ERROR_DEVICE_REMOVED" to "DirectX 장치 손실이 기록됐습니다. DXVK 또는 VKD3D 로그의 첫 오류를 확인하세요."
        )
        return signals.filter { log.contains(it.first, ignoreCase = true) }
            .joinToString("\n") { (signal, advice) -> "$signal · $advice" }
            .ifBlank { "알려진 오류 신호가 없습니다. 검은 화면의 원인은 이 로그만으로 확정하기 어렵습니다." }
    }

    fun optimize(original: String): GameConfigPreview.Preview {
        val current = GameConfigPreview.preview(original, "Render", mapOf("Resolution" to "1280x720"))
        val previous = current.changes.singleOrNull()?.before ?: return current
        val size = Regex("([0-9]{2,5})x([0-9]{2,5})").matchEntire(previous)
        require(size != null) { "해상도 형식을 확인할 수 없습니다." }
        val w = size.groupValues[1].toInt()
        val h = size.groupValues[2].toInt()
        require(w > 1280 && h > 720 && w * 9 == h * 16) { "현재 해상도와 화면비에는 720p 제안을 적용할 수 없습니다." }
        return current
    }
}
