package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProductHostReadiness(val rendererReady: Boolean, val controllerCanStart: Boolean)

interface EmbeddedProductExecution {
    suspend fun start(): EmbeddedWorkspaceRunResult?
    suspend fun close(): EmbeddedWorkspaceRunResult?
}

sealed interface ProductStartOutcome {
    data class NotReady(val reason: EmbeddedReadinessResult) : ProductStartOutcome
    data object Busy : ProductStartOutcome
    data object RendererChanged : ProductStartOutcome
    data class RunResult(val result: EmbeddedWorkspaceRunResult) : ProductStartOutcome
    data object Uncertain : ProductStartOutcome
}

class EmbeddedWorkspaceProductController(
    private val workspaceId: String,
    private val gate: EmbeddedProductRunGate,
    private val capabilityProbe: EmbeddedCapabilityProbe,
    private val hostReadiness: () -> ProductHostReadiness,
    private val execution: EmbeddedProductExecution,
    private val appRunScope: CoroutineScope,
) {
    private val mutex = Mutex()
    private var token: RunToken? = null
    private var closeRequested = false

    suspend fun start(): ProductStartOutcome = mutex.withLock {
        if (closeRequested || token != null) return@withLock ProductStartOutcome.Busy
        val host = hostReadiness()
        val readiness = EmbeddedWorkspaceReadiness.evaluate(
            capabilityProbe.snapshot(), true, host.rendererReady, host.controllerCanStart,
        )
        if (readiness != EmbeddedReadinessResult.Ready) return@withLock ProductStartOutcome.NotReady(readiness)
        val acquired = gate.tryAcquireEmbedded(workspaceId) ?: return@withLock ProductStartOutcome.Busy
        token = acquired
        try {
            val result = execution.start()
            if (result == null) {
                gate.releaseWithoutAllocation(acquired)
                token = null
                ProductStartOutcome.RendererChanged
            } else {
                gate.acceptResult(acquired, result)
                if (gate.state.value == ProductRunPhase.IDLE) token = null
                ProductStartOutcome.RunResult(result)
            }
        } catch (failure: Exception) {
            gate.markUncertain(acquired)
            ProductStartOutcome.Uncertain
        }
    }

    suspend fun requestExit(): Boolean = mutex.withLock {
        closeRequested = true
        val current = token ?: return@withLock gate.state.value == ProductRunPhase.IDLE
        gate.markStopping(current)
        val result = try { execution.close() } catch (failure: Exception) {
            gate.markUncertain(current)
            null
        }
        if (result != null) gate.acceptResult(current, result)
        else gate.markUncertain(current)
        val clean = gate.state.value == ProductRunPhase.IDLE
        if (clean) token = null
        clean
    }

    suspend fun observeResult(result: EmbeddedWorkspaceRunResult) = mutex.withLock {
        val current = token ?: return@withLock
        gate.acceptResult(current, result)
        if (gate.state.value == ProductRunPhase.IDLE) token = null
    }

    fun onHostDisposed(): Job = appRunScope.launch {
        try { requestExit() } finally { appRunScope.cancel() }
    }
}
