package com.trancong.dexworkspacetouch.feature.car

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CarWorkflowExecutionArbiter {
    private val mutableIsRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = mutableIsRunning.asStateFlow()

    @Synchronized
    fun tryAcquire(): Boolean {
        if (mutableIsRunning.value) return false
        mutableIsRunning.value = true
        return true
    }

    @Synchronized
    fun release() {
        mutableIsRunning.value = false
    }
}

sealed interface CarWorkflowExecutionState<out S> {
    data object Idle : CarWorkflowExecutionState<Nothing>
    data class Running<S>(val source: S, val workflowId: String) : CarWorkflowExecutionState<S>
    data class Error<S>(val source: S, val workflowId: String?, val message: String) :
        CarWorkflowExecutionState<S>
}

class CarWorkflowExecutionRunner<S>(
    private val actionEngine: CarActionEngine,
    private val scope: CoroutineScope,
    private val arbiter: CarWorkflowExecutionArbiter? = null,
) {
    private val mutableState = MutableStateFlow<CarWorkflowExecutionState<S>>(
        CarWorkflowExecutionState.Idle,
    )
    val state: StateFlow<CarWorkflowExecutionState<S>> = mutableState.asStateFlow()
    private var runningJob: Job? = null

    fun acceptAndRun(
        source: S,
        workflow: CarWorkflow,
        onAccepted: () -> Unit = {},
    ): Boolean {
        if (mutableState.value !is CarWorkflowExecutionState.Idle) return false
        if (arbiter?.tryAcquire() == false) return false
        mutableState.value = CarWorkflowExecutionState.Running(source, workflow.id)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                mutableState.value = when (val result = actionEngine.execute(workflow)) {
                    CarWorkflowResult.Success -> CarWorkflowExecutionState.Idle
                    is CarWorkflowResult.Failure -> CarWorkflowExecutionState.Error(
                        source,
                        workflow.id,
                        result.error.userMessage(),
                    )
                }
            } catch (cancellation: CancellationException) {
                mutableState.value = CarWorkflowExecutionState.Idle
                throw cancellation
            }
        }
        runningJob = job
        job.invokeOnCompletion {
            if (runningJob === job) runningJob = null
            arbiter?.release()
        }
        try {
            onAccepted()
        } catch (failure: RuntimeException) {
            mutableState.value = CarWorkflowExecutionState.Idle
            runningJob = null
            arbiter?.release()
            throw failure
        }
        job.start()
        return true
    }

    fun reject(source: S, message: String) {
        if (mutableState.value is CarWorkflowExecutionState.Idle) {
            mutableState.value = CarWorkflowExecutionState.Error(source, null, message)
        }
    }

    fun dismissError() {
        if (mutableState.value is CarWorkflowExecutionState.Error) {
            mutableState.value = CarWorkflowExecutionState.Idle
        }
    }

    fun dispose() {
        runningJob?.cancel()
        if (mutableState.value is CarWorkflowExecutionState.Running) {
            mutableState.value = CarWorkflowExecutionState.Idle
        }
    }
}

internal fun CarActionError.userMessage(): String = when (this) {
    CarActionError.AppUnavailable -> "Ứng dụng không khả dụng."
    CarActionError.UriUnavailable -> "Liên kết không khả dụng."
    CarActionError.WorkspaceUnavailable -> "Workspace không khả dụng."
    is CarActionError.UnsupportedAction -> "Hành động chưa được hỗ trợ."
    is CarActionError.ExecutionFailed -> message ?: "Không thể thực hiện hành động."
}
