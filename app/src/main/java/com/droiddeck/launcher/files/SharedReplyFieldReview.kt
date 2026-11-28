package com.droiddeck.launcher.files

/** Ephemeral editor review; never writes a draft or accepts an AI result. */
internal data class SharedReplyFieldReview private constructor(
    val requestId: String,
    val previous: String,
    val incoming: String,
) {
    fun matches(currentId: String?, currentText: String, currentInbox: String?): Boolean =
        currentId == requestId && currentText == previous && currentInbox == incoming

    companion object {
        fun create(requestId: String, currentText: String, incoming: String): SharedReplyFieldReview? =
            if (currentText.isBlank() || currentText == incoming) null
            else SharedReplyFieldReview(requestId, currentText, incoming)
    }
}
