package com.trancong.dexworkspacetouch.workspace.externaltransfer

sealed interface ExternalTransferDetection {
    data object SingleWorkspace : ExternalTransferDetection
    data object LibraryBundle : ExternalTransferDetection
    data object Invalid : ExternalTransferDetection
    data object UnsupportedVersion : ExternalTransferDetection
    data object FileTooLarge : ExternalTransferDetection
}

enum class ExternalTransferReadFailure {
    READ_FAILURE,
    PERMISSION_REVOKED,
    INVALID_CONTENT,
    UNSUPPORTED_VERSION,
    FILE_TOO_LARGE,
    MISSING_URI,
    MULTIPLE_URIS,
    INVALID_PAYLOAD,
}

data class ExternalTransferEvent(
    val identity: String,
    val bytes: ByteArray,
    val detection: ExternalTransferDetection,
)

sealed interface ExternalTransferInboxState {
    data object Idle : ExternalTransferInboxState
    data class Reading(val identity: String) : ExternalTransferInboxState
    data class Pending(val event: ExternalTransferEvent) : ExternalTransferInboxState
    data class Error(val identity: String, val failure: ExternalTransferReadFailure) : ExternalTransferInboxState
}
