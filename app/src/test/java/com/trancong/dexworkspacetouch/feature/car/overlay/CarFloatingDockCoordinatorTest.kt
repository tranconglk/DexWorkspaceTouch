package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.CarAction
import com.trancong.dexworkspacetouch.feature.car.CarActionEngine
import com.trancong.dexworkspacetouch.feature.car.CarActionError
import com.trancong.dexworkspacetouch.feature.car.CarActionExecutor
import com.trancong.dexworkspacetouch.feature.car.CarActionResult
import com.trancong.dexworkspacetouch.feature.car.CarWorkflow
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionState
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutPreferences
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcuts
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutWorkflowProvider
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarFloatingDockCoordinatorTest {
    @Test
    fun permissionRefreshAndInvalidHostShowUseRealSessionResults() = runBlocking {
        val session = FakeSession(permissionGranted = false)
        val coordinator = coordinator(session, RecordingExecutor())
        assertEquals(CarFloatingDockControlState.PermissionRequired, coordinator.dockState.value)

        coordinator.refresh()
        assertEquals(CarFloatingDockControlState.PermissionRequired, coordinator.dockState.value)
        session.permissionGranted = true
        coordinator.refresh()
        assertEquals(CarFloatingDockControlState.Hidden, coordinator.dockState.value)

        coordinator.reportDisplayUnavailable()
        assertEquals(CarFloatingDockControlState.DisplayUnavailable, coordinator.dockState.value)
        coordinator.dispose()
    }

    @Test
    fun shownAlreadyShownHideRevocationAndDisplayRemovalSynchronize() = runBlocking {
        val session = FakeSession()
        val coordinator = coordinator(session, RecordingExecutor())
        coordinator.show(fakeHost(21))
        assertEquals(CarFloatingDockControlState.Visible(21), coordinator.dockState.value)
        coordinator.show(fakeHost(42))
        assertEquals(CarFloatingDockControlState.Visible(21), coordinator.dockState.value)

        coordinator.hide()
        assertEquals(CarFloatingDockControlState.Hidden, coordinator.dockState.value)
        coordinator.show(fakeHost(21))
        session.permissionGranted = false
        coordinator.refresh()
        assertEquals(CarFloatingDockControlState.PermissionRequired, coordinator.dockState.value)

        session.permissionGranted = true
        coordinator.show(fakeHost(37))
        session.removeDisplay()
        yield()
        assertEquals(CarFloatingDockControlState.Hidden, coordinator.dockState.value)
        coordinator.dispose()
    }

    @Test
    fun productionOverlayActionsSelectExpectedWorkflowsAndReturnIdle() = runBlocking {
        listOf(
            CarWorkspaceShortcutSlot.Slot1 to "workspace-1",
            CarWorkspaceShortcutSlot.Slot2 to "workspace-2",
            CarWorkspaceShortcutSlot.Slot3 to "workspace-3",
        ).forEach { (slot, workspaceId) ->
            val session = FakeSession()
            val executor = RecordingExecutor()
            val coordinator = coordinator(session, executor)
            coordinator.show(fakeHost(21))
            session.expand()

            session.tap(slot)
            withTimeout(2_000) { coordinator.workflowState.first { it is CarWorkflowExecutionState.Idle } }

            assertEquals(listOf(CarAction.Workspace(workspaceId)), executor.actions)
            assertEquals(CarFloatingDockState.Collapsed, session.shownDockState)
            assertEquals(CarFloatingDockControlState.Visible(21), coordinator.dockState.value)
            assertEquals(0, session.hideCalls)
            coordinator.dispose()
        }
    }

    @Test
    fun missingBindingIsLightweightError() = runBlocking {
        val session = FakeSession()
        val coordinator = CarFloatingDockCoordinator(
            session,
            CarWorkspaceShortcutWorkflowProvider { null },
            CarActionEngine(RecordingExecutor()),
            this,
            FakePreferences(),
            FakeRepository(),
            com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter(),
        )
        coordinator.show(fakeHost(21))
        session.expand()
        session.tap(CarWorkspaceShortcutSlot.Slot1)
        assertTrue(coordinator.workflowState.value is CarWorkflowExecutionState.Error)
        assertEquals(CarFloatingDockState.Expanded, session.shownDockState)
        assertEquals(0, session.collapseCalls)
        coordinator.dispose()
    }

    @Test
    fun runningDisablesActionsAndRejectsSecondTap() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val session = FakeSession()
        val executor = RecordingExecutor(gate)
        val coordinator = coordinator(session, executor)
        coordinator.show(fakeHost(21))
        session.expand()

        session.tap(CarWorkspaceShortcutSlot.Slot1)
        session.forceTap(CarWorkspaceShortcutSlot.Slot2)
        withTimeout(2_000) { while (executor.actions.isEmpty()) yield() }
        assertEquals(false, session.actionInputsEnabled)
        assertTrue(coordinator.workflowState.value is CarWorkflowExecutionState.Running)
        assertEquals(1, executor.actions.size)
        assertEquals(1, session.collapseCalls)
        assertEquals(CarFloatingDockState.Collapsed, session.shownDockState)

        gate.complete(Unit)
        withTimeout(2_000) { coordinator.workflowState.first { it is CarWorkflowExecutionState.Idle } }
        withTimeout(2_000) { while (!session.actionInputsEnabled) yield() }
        assertEquals(true, session.actionInputsEnabled)
        coordinator.dispose()
    }

    @Test
    fun failureBecomesError_hideDoesNotChangeRunnerAndDisposeCancels() = runBlocking {
        val failedSession = FakeSession()
        val failed = RecordingExecutor(result = CarActionResult.Failure(CarActionError.AppUnavailable))
        val failedCoordinator = coordinator(failedSession, failed)
        failedCoordinator.show(fakeHost(21))
        failedSession.expand()
        failedSession.tap(CarWorkspaceShortcutSlot.Slot3)
        withTimeout(2_000) { failedCoordinator.workflowState.first { it is CarWorkflowExecutionState.Error } }
        assertEquals(CarFloatingDockState.Collapsed, failedSession.shownDockState)
        failedCoordinator.hide()
        assertTrue(failedCoordinator.workflowState.value is CarWorkflowExecutionState.Error)
        failedCoordinator.dispose()

        val gate = CompletableDeferred<Unit>()
        val runningSession = FakeSession()
        val runningCoordinator = coordinator(runningSession, RecordingExecutor(gate))
        runningCoordinator.show(fakeHost(21))
        runningSession.tap(CarWorkspaceShortcutSlot.Slot1)
        runningCoordinator.dispose()
        withTimeout(2_000) { runningCoordinator.workflowState.first { it is CarWorkflowExecutionState.Idle } }
        Unit
    }

    @Test
    fun visibleCountUpdatesPublishedDockItemsWithoutChangingSlotOrder() = runBlocking {
        val session = FakeSession()
        val preferences = FakePreferences()
        val coordinator = CarFloatingDockCoordinator(
            session,
            CarWorkspaceShortcutWorkflowProvider { null },
            CarActionEngine(RecordingExecutor()),
            this,
            preferences,
            FakeRepository(),
            com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter(),
        )

        withTimeout(2_000) { while (session.shortcuts.size != 6) yield() }
        preferences.setVisibleSlotCount(8)
        withTimeout(2_000) { while (session.shortcuts.size != 8) yield() }
        assertEquals(CarWorkspaceShortcutSlot.entries, session.shortcuts.map { it.slot })

        preferences.setVisibleSlotCount(4)
        withTimeout(2_000) { while (session.shortcuts.size != 4) yield() }
        assertEquals(CarWorkspaceShortcutSlot.entries.take(4), session.shortcuts.map { it.slot })
        coordinator.dispose()
    }

    @Test
    fun repositoryLayoutAndRenameUpdatesReachAnExpandedDockWithoutHideShow() = runBlocking {
        val session = FakeSession()
        val preferences = FakePreferences().apply {
            shortcuts.value = CarWorkspaceShortcuts.from {
                if (it == CarWorkspaceShortcutSlot.Slot1) "one" else null
            }
        }
        val repository = FakeRepository()
        val coordinator = CarFloatingDockCoordinator(
            session,
            CarWorkspaceShortcutWorkflowProvider { null },
            CarActionEngine(RecordingExecutor()),
            this,
            preferences,
            repository,
            com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter(),
        )
        coordinator.show(fakeHost(21))
        session.expand()

        repository.publish(workspace("one", "Original", listOf(
            WorkspaceCell("full", NormalizedBounds.FullCanvas),
        )))
        withTimeout(2_000) {
            while (session.shortcuts.firstOrNull()?.preview?.cells?.size != 1) yield()
        }
        repository.publish(workspace("one", "Renamed", listOf(
            WorkspaceCell("left", NormalizedBounds(0f, 0f, .5f, 1f)),
            WorkspaceCell("right", NormalizedBounds(.5f, 0f, 1f, 1f)),
        )))
        withTimeout(2_000) {
            while (session.shortcuts.firstOrNull()?.preview?.cells?.size != 2) yield()
        }

        assertEquals("Slot 1 — Renamed", session.shortcuts.first().accessibilityLabel)
        assertEquals(CarFloatingDockState.Expanded, session.shownDockState)
        assertEquals(0, session.hideCalls)
        coordinator.dispose()
    }

    private fun kotlinx.coroutines.CoroutineScope.coordinator(
        session: FakeSession,
        executor: CarActionExecutor,
    ) = CarFloatingDockCoordinator(
        session,
        CarWorkspaceShortcutWorkflowProvider { slot ->
            CarWorkflow(slot.stableWorkflowId, listOf(CarAction.Workspace("workspace-${slot.ordinal + 1}")))
        },
        CarActionEngine(executor),
        this,
        FakePreferences(),
        FakeRepository(),
        com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter(),
    )

    private fun fakeHost(displayId: Int): CarOverlayHost = object : CarOverlayHost {
        override val display = CarOverlayDisplay(displayId)
    }

    private class FakeSession(
        var permissionGranted: Boolean = true,
    ) : CarFloatingDockSession {
        private var callback: (CarWorkspaceShortcutSlot) -> Unit = {}
        var actionInputsEnabled = true
        var collapseCalls = 0
        var hideCalls = 0
        var shortcuts: List<CarFloatingWorkspaceShortcut> = emptyList()
        private val mutableStates = MutableStateFlow<CarOverlaySessionState>(CarOverlaySessionState.NotShown)
        override val stateChanges: StateFlow<CarOverlaySessionState> = mutableStates
        override var state: CarOverlaySessionState
            get() = mutableStates.value
            set(value) { mutableStates.value = value }
        override fun canDrawOverlays(): Boolean = permissionGranted
        override fun show(
            host: CarOverlayHost,
            onShortcut: (CarWorkspaceShortcutSlot) -> Unit,
        ): CarOverlayShowResult {
            if (!permissionGranted) return CarOverlayShowResult.PermissionDenied
            if (host.display.id == 0) return CarOverlayShowResult.DisplayUnavailable
            (state as? CarOverlaySessionState.Shown)?.let {
                return CarOverlayShowResult.AlreadyShown(it.displayId)
            }
            callback = onShortcut
            state = CarOverlaySessionState.Shown(host.display.id, CarFloatingDockState.Collapsed)
            return CarOverlayShowResult.Shown(host.display.id)
        }
        override fun hide() { hideCalls++; state = CarOverlaySessionState.NotShown }
        override fun collapse() {
            val shown = state as? CarOverlaySessionState.Shown ?: return
            if (shown.dockState == CarFloatingDockState.Collapsed) return
            collapseCalls++
            state = shown.copy(dockState = CarFloatingDockState.Collapsed)
        }
        override fun setActionsEnabled(enabled: Boolean) { actionInputsEnabled = enabled }
        override fun updateShortcuts(shortcuts: List<CarFloatingWorkspaceShortcut>) {
            this.shortcuts = shortcuts
        }
        override fun dispose() { state = CarOverlaySessionState.Disposed }
        fun tap(action: CarWorkspaceShortcutSlot) {
            if (actionInputsEnabled) callback(action)
        }
        fun forceTap(action: CarWorkspaceShortcutSlot) = callback(action)
        fun expand() {
            val shown = state as CarOverlaySessionState.Shown
            state = shown.copy(dockState = CarFloatingDockState.Expanded)
        }
        val shownDockState: CarFloatingDockState
            get() = (state as CarOverlaySessionState.Shown).dockState
        fun removeDisplay() {
            state = CarOverlaySessionState.NotShown
        }
    }

    private class FakePreferences : CarWorkspaceShortcutPreferences {
        override val shortcuts = MutableStateFlow(CarWorkspaceShortcuts.defaults())
        override val visibleSlotCount = MutableStateFlow(6)
        override fun setWorkspace(slot: CarWorkspaceShortcutSlot, workspaceId: String) = Unit
        override fun clear(slot: CarWorkspaceShortcutSlot) = Unit
        override fun setVisibleSlotCount(count: Int) { visibleSlotCount.value = count }
        override fun refresh() = Unit
    }

    private class FakeRepository : WorkspaceRepository {
        private val values = MutableStateFlow<List<Workspace>>(emptyList())
        override fun observeAll() = values
        override suspend fun getById(id: String) = values.value.firstOrNull { it.id == id }
        override suspend fun insert(workspace: Workspace) = Unit
        override suspend fun update(workspace: Workspace) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun exists(id: String) = false
        override suspend fun count() = 0
        fun publish(vararg workspaces: Workspace) { values.value = workspaces.toList() }
    }

    private class RecordingExecutor(
        private val gate: CompletableDeferred<Unit>? = null,
        private val result: CarActionResult = CarActionResult.Success,
    ) : CarActionExecutor {
        val actions = mutableListOf<CarAction>()
        override suspend fun execute(action: CarAction): CarActionResult {
            actions += action
            gate?.await()
            return result
        }
    }

    private fun workspace(id: String, name: String, cells: List<WorkspaceCell>) = Workspace(
        id = id,
        name = name,
        canvas = WorkspaceCanvas(cells),
        modifiedSequence = 0,
        schemaVersion = 1,
        createdAtEpochMillis = 0,
        updatedAtEpochMillis = 0,
    )
}
