package com.trancong.dexworkspacetouch.workspace.externaltransfer

data class ExternalTransferIntentPayload(
    val action: String?,
    val dataUri: String? = null,
    val extraStreamUri: String? = null,
    val clipUris: List<String> = emptyList(),
    val invalidStreamPayload: Boolean = false,
)

sealed interface ExternalTransferIntentResult {
    data class SingleUri(val uri: String) : ExternalTransferIntentResult
    data object MissingUri : ExternalTransferIntentResult
    data object MultipleUris : ExternalTransferIntentResult
    data object UnsupportedAction : ExternalTransferIntentResult
    data object InvalidPayload : ExternalTransferIntentResult
}

object ExternalTransferIntentParser {
    const val ActionView = "android.intent.action.VIEW"
    const val ActionSend = "android.intent.action.SEND"

    fun parse(payload: ExternalTransferIntentPayload): ExternalTransferIntentResult {
        val candidates = when (payload.action) {
            ActionView -> listOfNotNull(payload.dataUri)
            ActionSend -> {
                if (payload.invalidStreamPayload) return ExternalTransferIntentResult.InvalidPayload
                listOfNotNull(payload.extraStreamUri) + payload.clipUris
            }
            else -> return ExternalTransferIntentResult.UnsupportedAction
        }
        if (candidates.any { !it.isContentUri() }) return ExternalTransferIntentResult.InvalidPayload
        val distinct = candidates.distinct()
        return when (distinct.size) {
            0 -> ExternalTransferIntentResult.MissingUri
            1 -> ExternalTransferIntentResult.SingleUri(distinct.single())
            else -> ExternalTransferIntentResult.MultipleUris
        }
    }
}

private fun String.isContentUri(): Boolean = startsWith("content://", ignoreCase = true)
