package com.trancong.dexworkspacetouch.workspace.launcher.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyDisplayWorkAreaReferenceStore
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter

class WorkspaceLaunchViewModel(
    private val createReadiness: (WorkspaceLibraryItem) -> LaunchReadiness,
    private val suppliedScope: CoroutineScope? = null,
    private val executionArbiter: CarWorkflowExecutionArbiter? = null,
    private val onClassicLaunchStarted: (String) -> Unit = {},
    private val onClassicLaunchCompleted: (WorkspaceLaunchRequest) -> Unit = {},
) : ViewModel() {
    var state by mutableStateOf<WorkspaceLaunchUiState>(WorkspaceLaunchUiState.Idle)
        private set

    val legacyReferenceStore = LegacyDisplayWorkAreaReferenceStore()
    private var launchJob: Job? = null
    private var activeHostToken: Any? = null
    private var activeWorkspace: WorkspaceLibraryItem? = null

    fun launchWorkspace(item: WorkspaceLibraryItem, runtime: WorkspaceLaunchRuntime, hostToken: Any) {
        if (launchJob?.isActive == true) return
        if (executionArbiter?.tryAcquire() == false) return
        // Repair status is optional; it cannot block Classic or retain its reservation.
        runCatching { onClassicLaunchStarted(item.id) }
        activeHostToken = hostToken
        activeWorkspace = item
        launchJob = (suppliedScope ?: viewModelScope).launch(start = CoroutineStart.LAZY) {
            state = WorkspaceLaunchUiState.Checking(item.id)
            val readiness = createReadiness(item)
            if (readiness !is LaunchReadiness.Ready) {
                state = WorkspaceLaunchUiState.ReadinessError(item.id, readiness)
                return@launch
            }
            when (val environment = runtime.checkEnvironment()) {
                LaunchEnvironmentCheck.Ready -> Unit
                is LaunchEnvironmentCheck.Unavailable -> {
                    state = WorkspaceLaunchUiState.LaunchError(item.id, item.name, environment.reason)
                    return@launch
                }
            }
            state = WorkspaceLaunchUiState.Launching(item.id, item.name)
            try {
                val result = runtime.launch(readiness.request)
                state = WorkspaceLaunchUiState.Completed(
                    item.id,
                    item.name,
                    readiness.request.targets.size,
                    result,
                )
                if (result is WorkspaceLaunchResult.Success) {
                    runCatching { onClassicLaunchCompleted(readiness.request) }
                }
            } catch (cancellation: CancellationException) {
                state = WorkspaceLaunchUiState.Cancelled(item.id, item.name)
                throw cancellation
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (launchJob === job) launchJob = null
                executionArbiter?.release()
            }
        }
        launchJob?.start()
    }

    fun cancelLaunch() {
        val active = activeWorkspace ?: return
        launchJob?.cancel()
        state = WorkspaceLaunchUiState.Cancelled(active.id, active.name)
    }

    fun onHostDisposed(hostToken: Any) {
        if (activeHostToken != hostToken || launchJob?.isActive != true) return
        val active = activeWorkspace ?: return
        launchJob?.cancel()
        state = WorkspaceLaunchUiState.LaunchError(
            active.id,
            active.name,
            LaunchEnvironmentFailure.WORK_AREA_UNAVAILABLE,
        )
    }

    fun dismissResult() {
        if (launchJob?.isActive != true) state = WorkspaceLaunchUiState.Idle
    }

    override fun onCleared() {
        launchJob?.cancel()
    }

    companion object {
        fun factory(requestFactory: WorkspaceLaunchRequestFactory,
            executionArbiter: CarWorkflowExecutionArbiter? = null,
            onClassicLaunchStarted: (String) -> Unit = {},
            onClassicLaunchCompleted: (WorkspaceLaunchRequest) -> Unit = {}): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(WorkspaceLaunchViewModel::class.java))
                    return WorkspaceLaunchViewModel(
                        createReadiness = { item ->
                            requestFactory.create(item.id, item.name, item.canvas)
                        },
                        executionArbiter = executionArbiter,
                        onClassicLaunchStarted = onClassicLaunchStarted,
                        onClassicLaunchCompleted = onClassicLaunchCompleted,
                    ) as T
                }
            }
    }
}
