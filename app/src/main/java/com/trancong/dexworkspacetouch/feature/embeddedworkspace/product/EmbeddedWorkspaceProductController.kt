package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedResultEvidence

import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    hostReadiness: () -> ProductHostReadiness,
    execution: EmbeddedProductExecution,
    private val appRunScope: CoroutineScope,
    private val evidenceGraphId: String? = null,
) {
    private val lock = Any()
    private var hostReadiness: (() -> ProductHostReadiness)? = hostReadiness
    private var execution: EmbeddedProductExecution? = execution
    private var operation: ProductRunOperation? = null
    val startOperation: ProductRunOperation? get() = synchronized(lock) { operation }
    private var startWork: Deferred<ProductStartOutcome>? = null
    private var cleanupWork: Deferred<Boolean>? = null
    private var cleanupCompleted: Boolean? = null
    private var disposalWork: Job? = null
    private var closeRequested = false
    private var backCallback: (() -> Unit)? = null
    private var backWork: Job? = null
    private var hostDisposed = false
    private val mutableRecovery = MutableStateFlow(EmbeddedProductRecoveryMapper.readiness(
        EmbeddedReadinessResult.Ready, gate.state.value, gate.canEnterEmbedded(),
    ))
    val recovery = mutableRecovery.asStateFlow()

    fun currentRecovery(): EmbeddedProductRecovery {
        val status = gate.status.value
        val local = mutableRecovery.value
        return if (status.token != null) recoveryFrom(status) else
            EmbeddedProductRecoveryMapper.snapshot(status.phase, gate.canEnterEmbedded(),
                local.issue, local.allocationEvidence, local.cleanupEvidence)
    }

    fun canOpenClassic(): Boolean = EmbeddedRecoveryAction.OPEN_CLASSIC in currentRecovery().permittedActions

    suspend fun start(): ProductStartOutcome {
        val pending = synchronized(lock) {
            if (closeRequested || operation != null || execution == null || !appRunScope.isActive) {
                mutableRecovery.value = currentRecovery()
                return ProductStartOutcome.Busy
            }
            val host = checkNotNull(hostReadiness).invoke()
            val readiness = EmbeddedWorkspaceReadiness.evaluate(
                capabilityProbe.snapshot(), true, host.rendererReady, host.controllerCanStart,
            )
            if (readiness != EmbeddedReadinessResult.Ready) {
                val mapped = EmbeddedProductRecoveryMapper.readiness(readiness, gate.state.value, gate.canEnterEmbedded())
                val previous = mutableRecovery.value
                mutableRecovery.value = if (previous.cleanupEvidence == CleanupEvidence.UNCERTAIN ||
                    previous.cleanupEvidence == CleanupEvidence.INCOMPLETE) {
                    EmbeddedProductRecoveryMapper.snapshot(mapped.phase, gate.canEnterEmbedded(), mapped.issue,
                        previous.allocationEvidence, previous.cleanupEvidence)
                } else mapped
                return ProductStartOutcome.NotReady(readiness)
            }
            val acquired = gate.tryAcquireEmbedded(workspaceId) ?: return ProductStartOutcome.Busy
            val start = checkNotNull(gate.startOperation(acquired))
            operation = start
            EmbeddedEvidence.app("product.operation", graph = evidenceGraphId, fields = mapOf(
                "token_identity" to System.identityHashCode(acquired).toString(),
                "product_generation" to start.generation.toString(), "start_operation" to start.operationId.toString()))
            mutableRecovery.value = recoveryFrom(gate.status.value)
            val checkedHost = checkNotNull(hostReadiness).invoke()
            if (!checkedHost.rendererReady || !checkedHost.controllerCanStart) {
                val proof = checkNotNull(gate.preRunnerRejection(start))
                check(gate.releaseWithoutAllocation(proof))
                mutableRecovery.value = EmbeddedProductRecoveryMapper.readiness(
                    EmbeddedReadinessResult.RendererNotReady, gate.state.value, gate.canEnterEmbedded())
                // Chưa invoke runner, nhưng graph renderer/runner đã chuẩn bị vẫn cần local Close.
                // Không tạo cleanup operation/token và không đổi proof release của Task 3B.
                closeExecution(null)
                detachRuntime()
                return ProductStartOutcome.RendererChanged
            }
            val currentExecution = checkNotNull(execution)
            // UNDISPATCHED đi vào invocation trước khi thả lock, rồi trả lock ở suspension đầu tiên.
            // Không giữ lock/mutex suốt thời gian chờ Start; Back có thể ghi Stop intent ngay.
            appRunScope.async(start = CoroutineStart.UNDISPATCHED) {
                check(gate.markInvoked(start))
                val result = try { currentExecution.start() }
                catch (error: Exception) {
                    EmbeddedEvidence.observe { EmbeddedEvidence.app("product.start.failure", graph = evidenceGraphId,
                        fields = EmbeddedEvidence.errorFields(error)) }
                    null
                }
                if (result == null) {
                    synchronized(lock) {
                        if (gate.recordMissingStartResult(start)) mutableRecovery.value = recoveryFrom(gate.status.value)
                    }
                    ProductStartOutcome.Uncertain
                } else {
                    EmbeddedResultEvidence.record(result, evidenceGraphId, "product.raw")
                    val accepted = synchronized(lock) { consume(start, ProductExecutionValue.from(result)) }
                    if (accepted) ProductStartOutcome.RunResult(result) else ProductStartOutcome.Uncertain
                }
            }.also { startWork = it }
        }
        return try { pending.await() }
        catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancelled
            ProductStartOutcome.Uncertain
        } finally {
            synchronized(lock) { if (pending.isCompleted && startWork === pending) startWork = null }
        }
    }

    suspend fun requestExit(): Boolean {
        val pending = synchronized(lock) {
            closeRequested = true
            cleanupCompleted?.let { return it && gate.state.value == ProductRunPhase.IDLE }
            cleanupWork?.let { return@synchronized it }
            val current = operation
            val cleanup = current?.let { gate.markStopping(it) }
            if (current != null && cleanup == null) return gate.state.value == ProductRunPhase.IDLE
            if (cleanup != null) mutableRecovery.value = recoveryFrom(gate.status.value)
            closeExecution(cleanup)
        }
        return try { pending.await() } finally {
            synchronized(lock) { if (pending.isCompleted && cleanupWork === pending) cleanupWork = null }
        }
    }

    suspend fun observeResult(operation: ProductRunOperation, result: EmbeddedWorkspaceRunResult) = synchronized(lock) {
        EmbeddedResultEvidence.record(result, evidenceGraphId, "product.observed")
        if (operation != this.operation) {
            EmbeddedEvidence.app("product.acceptance", graph = evidenceGraphId, fields = mapOf("accepted" to "false", "disposition" to "operation_mismatch"))
            return@synchronized false
        }
        val value = ProductExecutionValue.from(result)
        if (!consume(operation, value)) return@synchronized false
        if (value.kind != ProductResultKind.STARTED) {
            startWork?.cancel()
            startWork = null
        }
        true
    }

    fun requestBack(onBack: () -> Unit): Job = synchronized(lock) {
        backWork ?: run {
            if (!hostDisposed) backCallback = onBack
            appRunScope.launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    if (requestExit()) synchronized(lock) {
                        if (!hostDisposed && gate.status.value.phase == ProductRunPhase.IDLE) {
                            val callback = backCallback
                            backCallback = null
                            callback?.invoke()
                        }
                    }
                } finally { synchronized(lock) { backCallback = null } }
            }.also { backWork = it }
        }
    }

    fun onHostDisposed(): Job = synchronized(lock) {
        hostDisposed = true
        backCallback = null
        backWork?.cancel()
        disposalWork ?: appRunScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { requestExit() } finally {
                synchronized(lock) { detachRuntime() }
                appRunScope.cancel()
            }
        }.also { disposalWork = it }
    }

    // Cả Back/Dispose và prepared graph chưa invoke đều join cùng local Close.
    // Chỉ cleanup operation của run đã acquire mới được gửi kết quả vào gate.
    private fun closeExecution(cleanup: ProductRunOperation?): Deferred<Boolean> {
        val currentExecution = execution
        return appRunScope.async(start = CoroutineStart.UNDISPATCHED) {
            val result = try { currentExecution?.close() }
            catch (error: Exception) {
                EmbeddedEvidence.observe { EmbeddedEvidence.app("product.close.failure", graph = evidenceGraphId,
                    fields = EmbeddedEvidence.errorFields(error)) }
                null
            }
            result?.let { EmbeddedResultEvidence.record(it, evidenceGraphId, "product.cleanup") }
            synchronized(lock) {
                if (cleanup != null) consume(cleanup, ProductExecutionValue.from(result))
                startWork?.cancel()
                startWork = null
                cleanupCompleted = gate.state.value == ProductRunPhase.IDLE
                detachRuntime()
                checkNotNull(cleanupCompleted)
            }
        }.also { cleanupWork = it }
    }

    private fun consume(operation: ProductRunOperation, value: ProductExecutionValue): Boolean {
        EmbeddedEvidence.app("product.projection", graph = evidenceGraphId, fields = buildMap {
            put("product_generation", operation.generation.toString())
            put(if (operation.kind == ProductOperationKind.START) "start_operation" else "cleanup_operation", operation.operationId.toString())
            put("result_kind", value.kind.name); put("authoritative_clean", value.authoritativeClean.toString())
            put("allocation_evidence", value.allocationEvidence.name); put("cleanup_evidence", value.cleanupEvidence.name)
            value.issue?.let { put("issue_code", it.code) }
        })
        val accepted = gate.acceptResult(operation, value)
        EmbeddedEvidence.app("product.acceptance", graph = evidenceGraphId, fields = mapOf("accepted" to accepted.toString()))
        if (!accepted) return false
        mutableRecovery.value = recoveryFrom(gate.status.value)
        if (value.kind != ProductResultKind.STARTED) {
            cleanupCompleted = gate.state.value == ProductRunPhase.IDLE
            detachRuntime()
        }
        return true
    }

    private fun recoveryFrom(status: ProductRunStatus): EmbeddedProductRecovery =
        EmbeddedProductRecoveryMapper.snapshot(status.phase, gate.canEnterEmbedded(),
            status.issue, status.allocationEvidence, status.cleanupEvidence)

    private fun detachRuntime() {
        execution = null
        hostReadiness = null
    }
}
