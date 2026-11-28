package com.droiddeck.launcher.files

/** Bounded file authoring; these are capacity limits, not measured device performance. */
internal object TranslationLimits {
    const val MAX_ROWS = 500
    const val MAX_TEXT_CHARS = 1000
    const val MAX_SOURCE_CHARS = 262144
    const val MAX_PATCH_BYTES = 1024 * 1024
    const val MAX_BATCH_BYTES = MAX_PATCH_BYTES
    const val MAX_BATCH_ENTRIES = MAX_ROWS + 2
    const val MAX_DRAFT_BYTES = 2 * 1024 * 1024
}
