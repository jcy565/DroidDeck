package com.droiddeck.launcher.files

import java.io.File

internal object ImageOcrRuntime {
    const val enabled = false
    suspend fun recognize(file: File, allowed: () -> Boolean): String =
        error("이 빌드에는 실험용 이미지 인식 기능이 포함되지 않았습니다.")
}
