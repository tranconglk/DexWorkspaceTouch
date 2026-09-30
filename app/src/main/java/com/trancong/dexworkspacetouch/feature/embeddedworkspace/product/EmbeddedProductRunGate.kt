package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceCleanupOutcome
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock

enum class ProductRunPhase { IDLE, STARTING, ACTIVE, STOPPING, CLEANUP_BLOCKED }

class RunToken internal constructor(val workspaceId: String)

class EmbeddedProductRunGate {
    private val lock = ReentrantLock()
    private val mutableState = MutableStateFlow(ProductRunPhase.IDLE)
    val state: StateFlow<ProductRunPhase> = mutableState.asStateFlow()
    private var owner: RunToken? = null
    private var classicDispatching = false

    fun canEnterEmbedded(): Boolean = lock.withGate { owner == null && !classicDispatching }

    fun tryAcquireEmbedded(workspaceId: String): RunToken? {
        if (!lock.tryLock()) return null
        return try {
            if (owner != null || classicDispatching || workspaceId.isBlank()) null
            else RunToken(workspaceId).also {
                owner = it
                mutableState.value = ProductRunPhase.STARTING
            }
        } finally { lock.unlock() }
    }

    fun tryDispatchClassic(dispatch: () -> Unit): Boolean {
        if (!lock.tryLock()) return false
        return try {
            if (owner != null || classicDispatching) false
            else {
                classicDispatching = true
                try { dispatch() } finally { classicDispatching = false }
                true
            }
        } finally { lock.unlock() }
    }

    fun markStopping(token: RunToken) = lock.withGate {
        if (owner === token && mutableState.value != ProductRunPhase.CLEANUP_BLOCKED) {
            mutableState.value = ProductRunPhase.STOPPING
        }
    }

    fun markUncertain(token: RunToken) = lock.withGate {
        if (owner === token) mutableState.value = ProductRunPhase.CLEANUP_BLOCKED
    }

    fun releaseWithoutAllocation(token: RunToken) = lock.withGate {
        if (owner === token) release()
    }

    fun acceptResult(token: RunToken, result: EmbeddedWorkspaceRunResult) = lock.withGate {
        if (owner !== token) return@withGate
        when (result) {
            is EmbeddedWorkspaceRunResult.Started -> mutableState.value = ProductRunPhase.ACTIVE
            is EmbeddedWorkspaceRunResult.PreflightRejected -> release()
            is EmbeddedWorkspaceRunResult.StartFailed -> {
                if (result.allOwnedSessionsClean) release()
                else mutableState.value = ProductRunPhase.CLEANUP_BLOCKED
            }
            is EmbeddedWorkspaceRunResult.Stopped -> {
                if (result.cleanupOutcomes.all { it is EmbeddedWorkspaceCleanupOutcome.Clean }) release()
                else mutableState.value = ProductRunPhase.CLEANUP_BLOCKED
            }
            is EmbeddedWorkspaceRunResult.CleanupIncomplete,
            is EmbeddedWorkspaceRunResult.RecoveryRequired,
            EmbeddedWorkspaceRunResult.DuplicateCall -> mutableState.value = ProductRunPhase.CLEANUP_BLOCKED
        }
    }

    private fun release() {
        owner = null
        mutableState.value = ProductRunPhase.IDLE
    }

    private inline fun <T> ReentrantLock.withGate(action: () -> T): T {
        lock()
        return try { action() } finally { unlock() }
    }
}
