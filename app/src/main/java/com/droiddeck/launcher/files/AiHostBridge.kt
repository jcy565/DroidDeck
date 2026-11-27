package com.droiddeck.launcher.files

import com.droiddeck.ai.core.AiCore

/** Engine-owned contract. Increment only when the reviewed AI-facing API changes. */
internal object AiHostBridge {
    const val HOST_API = 1
    const val MIN_DRAFT_SCHEMA = 1
    const val MAX_DRAFT_SCHEMA = 1

    data class Availability(val version: String?, val reason: String?) {
        val enabled: Boolean get() = reason == null
    }

    fun availability(): Availability = try {
        when (AiCore.compatibility(HOST_API, MIN_DRAFT_SCHEMA, MAX_DRAFT_SCHEMA)) {
            AiCore.Status.COMPATIBLE -> Availability(AiCore.version(), null)
            AiCore.Status.HOST_API_MISMATCH -> Availability(AiCore.version(), "공식 엔진과 AI API 버전이 호환되지 않습니다.")
            AiCore.Status.DRAFT_SCHEMA_MISMATCH -> Availability(AiCore.version(), "AI 작업 데이터 형식이 호환되지 않습니다.")
            else -> Availability(null, "알 수 없는 AI 호환성 상태입니다.")
        }
    } catch (_: LinkageError) {
        Availability(null, "AI 연결 모듈을 불러올 수 없습니다.")
    }

    fun requireCompatible() {
        val state = availability()
        check(state.enabled) { state.reason ?: "AI 모듈 비활성화" }
    }
}
