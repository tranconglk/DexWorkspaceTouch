package com.trancong.dexworkspacetouch.feature.car.overlay

import android.content.Context
import com.trancong.dexworkspacetouch.feature.car.CarActionEngine
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionRunner
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionState
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutPreferences
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutWorkflowProvider
import com.trancong.dexworkspacetouch.feature.car.PreferencesCarWorkspaceShortcutWorkflowProvider
import com.trancong.dexworkspacetouch.feature.car.platform.AndroidCarActionExecutor
import com.trancong.dexworkspacetouch.feature.car.platform.RepositoryCarWorkspaceLaunchPlatform
import com.trancong.dexworkspacetouch.platform.launch.android.DisplayTargetWorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

sealed interface CarFloatingDockControlState {
    data object Hidden : CarFloatingDockControlState
    data class Visible(val displayId: Int) : CarFloatingDockControlState
    data object PermissionRequired : CarFloatingDockControlState
    data object DisplayUnavailable : CarFloatingDockControlState
    data class Error(val message: String) : CarFloatingDockControlState
}

class CarFloatingDockCoordinator(
    private val session: CarFloatingDockSession,
    private val workflowProvider: CarWorkspaceShortcutWorkflowProvider,
    actionEngine: CarActionEngine,
    scope: CoroutineScope,
    shortcutPreferences: CarWorkspaceShortcutPreferences,
    workspaceRepository: WorkspaceRepository,
    private val executionArbiter: CarWorkflowExecutionArbiter,
) {
    private val runner = CarWorkflowExecutionRunner<CarWorkspaceShortcutSlot>(
        actionEngine,
        scope,
        executionArbiter,
    )
    private val mutableDockState = MutableStateFlow<CarFloatingDockControlState>(
        CarFloatingDockControlState.Hidden,
    )
    val dockState: StateFlow<CarFloatingDockControlState> = mutableDockState.asStateFlow()
    val workflowState: StateFlow<CarWorkflowExecutionState<CarWorkspaceShortcutSlot>> = runner.state
    private var currentShortcuts: List<CarFloatingWorkspaceShortcut> = emptyList()
    private val runnerStateJob: Job = scope.launch {
        executionArbiter.isRunning.collect { isRunning ->
            session.setActionsEnabled(!isRunning)
        }
    }
    private val shortcutStateJob: Job = scope.launch {
        combine(
            shortcutPreferences.shortcuts,
            shortcutPreferences.visibleSlotCount,
            workspaceRepository.observeAll(),
        ) { shortcuts, visibleSlotCount, workspaces ->
            resolveFloatingWorkspaceShortcuts(
                shortcuts,
                workspaces.associateBy { it.id },
                visibleSlotCount,
            )
        }.collect { shortcuts ->
            currentShortcuts = shortcuts
            session.updateShortcuts(shortcuts)
        }
    }
    private val sessionStateJob: Job = scope.launch {
        session.stateChanges.collect { synchronizeState() }
    }

    init {
        refresh()
    }

    fun show(host: CarOverlayHost) {
        mutableDockState.value = when (val result = session.show(host, ::run)) {
            is CarOverlayShowResult.Shown -> CarFloatingDockControlState.Visible(result.displayId)
            is CarOverlayShowResult.AlreadyShown -> CarFloatingDockControlState.Visible(result.displayId)
            CarOverlayShowResult.PermissionDenied -> CarFloatingDockControlState.PermissionRequired
            CarOverlayShowResult.DisplayUnavailable -> CarFloatingDockControlState.DisplayUnavailable
            CarOverlayShowResult.WindowUnavailable -> CarFloatingDockControlState.Error(
                "Floating Dock could not be shown.",
            )
            CarOverlayShowResult.SessionDisposed -> CarFloatingDockControlState.Error(
                "Floating Dock session is unavailable.",
            )
        }
        session.updateShortcuts(currentShortcuts)
        session.setActionsEnabled(!executionArbiter.isRunning.value)
    }

    fun reportDisplayUnavailable() {
        mutableDockState.value = CarFloatingDockControlState.DisplayUnavailable
    }

    fun hide() {
        session.hide()
        mutableDockState.value = CarFloatingDockControlState.Hidden
    }

    fun dismissError() = runner.dismissError()

    fun refresh() {
        if (!session.canDrawOverlays()) session.hide()
        synchronizeState()
    }

    fun dispose() {
        runner.dispose()
        runnerStateJob.cancel()
        shortcutStateJob.cancel()
        sessionStateJob.cancel()
        session.dispose()
        mutableDockState.value = CarFloatingDockControlState.Hidden
    }

    private fun run(slot: CarWorkspaceShortcutSlot) {
        val workflow = workflowProvider.workflowFor(slot)
        if (workflow == null) {
            runner.reject(slot, "Workspace shortcut is unavailable.")
        } else {
            runner.acceptAndRun(slot, workflow, session::collapse)
        }
        session.setActionsEnabled(!executionArbiter.isRunning.value)
    }

    private fun synchronizeState() {
        mutableDockState.value = when {
            !session.canDrawOverlays() -> CarFloatingDockControlState.PermissionRequired
            session.state is CarOverlaySessionState.Shown -> CarFloatingDockControlState.Visible(
                (session.state as CarOverlaySessionState.Shown).displayId,
            )
            session.state is CarOverlaySessionState.Disposed -> CarFloatingDockControlState.Error(
                "Floating Dock session is unavailable.",
            )
            else -> CarFloatingDockControlState.Hidden
        }
    }

    companion object {
        fun create(
            context: Context,
            scope: CoroutineScope,
            workspaceRepository: WorkspaceRepository,
            shortcutPreferences: CarWorkspaceShortcutPreferences,
            executionArbiter: CarWorkflowExecutionArbiter,
        ): CarFloatingDockCoordinator {
            val applicationContext = context.applicationContext
            val session = CarOverlaySession(
                AndroidCarOverlayPermissionChecker(applicationContext),
                AndroidCarFloatingDockWindowFactory(applicationContext),
                AndroidCarOverlayDisplayEvents(applicationContext),
            )
            val displayProvider = {
                (session.activeHost as? AndroidCarOverlayHost)?.androidDisplay
            }
            val requestFactory = WorkspaceLaunchRequestFactory(
                DefaultInstalledAppCatalog(
                    AndroidInstalledAppDataSource.create(applicationContext),
                ),
            )
            val workspacePlatform = RepositoryCarWorkspaceLaunchPlatform(
                repository = workspaceRepository,
                requestFactory = requestFactory,
                runtime = DisplayTargetWorkspaceLaunchRuntime(
                    applicationContext,
                    displayProvider,
                ),
            )
            val executor = AndroidCarActionExecutor.createForOverlay(
                context = applicationContext,
                displayProvider = displayProvider,
                workspaceLaunchPlatform = workspacePlatform,
            )
            return CarFloatingDockCoordinator(
                session = session,
                workflowProvider = PreferencesCarWorkspaceShortcutWorkflowProvider(
                    shortcutPreferences,
                ),
                actionEngine = CarActionEngine(executor),
                scope = scope,
                shortcutPreferences = shortcutPreferences,
                workspaceRepository = workspaceRepository,
                executionArbiter = executionArbiter,
            )
        }
    }
}
