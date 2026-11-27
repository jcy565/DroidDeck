package com.droiddeck.launcher.files

import android.content.Context
import java.io.File

internal object LocalAgentRuntime {
    const val enabled = false
    fun <T> withIdleModelStore(work: () -> T): T =
        error("이 빌드에는 실험용 로컬 모델 런타임이 포함되지 않았습니다.")
    fun openSelected(context: Context, allowed: () -> Boolean, select: () -> File): GameAgentModel =
        error("이 빌드에는 실험용 로컬 모델 런타임이 포함되지 않았습니다.")
    fun open(context: Context, file: File, allowed: () -> Boolean): GameAgentModel =
        error("이 빌드에는 실험용 로컬 모델 런타임이 포함되지 않았습니다.")
}
