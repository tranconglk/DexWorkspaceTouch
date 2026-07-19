package com.trancong.dexworkspacetouch.workspace.externaltransfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ExternalTransferViewModel : ViewModel() {
    var state by mutableStateOf<ExternalTransferInboxState>(ExternalTransferInboxState.Idle)
        private set
    private val acceptedIdentities = mutableSetOf<String>()

    fun begin(identity: String, allowConsumedReplay: Boolean = false): Boolean {
        if (identity.isBlank() || state !is ExternalTransferInboxState.Idle) return false
        if (allowConsumedReplay) acceptedIdentities.remove(identity)
        if (!acceptedIdentities.add(identity)) return false
        state = ExternalTransferInboxState.Reading(identity)
        return true
    }

    fun accept(identity: String, bytes: ByteArray, detection: ExternalTransferDetection) {
        if ((state as? ExternalTransferInboxState.Reading)?.identity != identity) return
        state = when (detection) {
            ExternalTransferDetection.Invalid -> ExternalTransferInboxState.Error(identity, ExternalTransferReadFailure.INVALID_CONTENT)
            ExternalTransferDetection.UnsupportedVersion -> ExternalTransferInboxState.Error(identity, ExternalTransferReadFailure.UNSUPPORTED_VERSION)
            ExternalTransferDetection.FileTooLarge -> ExternalTransferInboxState.Error(identity, ExternalTransferReadFailure.FILE_TOO_LARGE)
            else -> ExternalTransferInboxState.Pending(ExternalTransferEvent(identity, bytes, detection))
        }
    }

    fun fail(identity: String, failure: ExternalTransferReadFailure) {
        if ((state as? ExternalTransferInboxState.Reading)?.identity == identity) {
            state = ExternalTransferInboxState.Error(identity, failure)
        }
    }

    fun consume(identity: String) {
        val currentIdentity = when (val current = state) {
            is ExternalTransferInboxState.Pending -> current.event.identity
            is ExternalTransferInboxState.Error -> current.identity
            else -> null
        }
        if (currentIdentity == identity) state = ExternalTransferInboxState.Idle
    }
}
