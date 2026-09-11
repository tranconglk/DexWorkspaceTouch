package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class CarOverlayDisplay(val id: Int)

interface CarOverlayHost {
    val display: CarOverlayDisplay
}

fun interface CarOverlayPermissionChecker {
    fun canDrawOverlays(): Boolean
}

interface CarFloatingDockWindow {
    val display: CarOverlayDisplay
    val dockState: CarFloatingDockState
    fun expand()
    fun collapse()
    fun setActionsEnabled(enabled: Boolean)
    fun updateShortcuts(shortcuts: List<CarFloatingWorkspaceShortcut>)
    fun remove()
    fun performHandleClickForTest(): Boolean = false
    fun performShortcutClickForTest(slot: CarWorkspaceShortcutSlot): Boolean = false
    fun performCollapseClickForTest(): Boolean = false
    fun positionForTest(): CarFloatingDockPosition? = null
}

interface CarFloatingDockSession {
    val state: CarOverlaySessionState
    val stateChanges: StateFlow<CarOverlaySessionState>
    val activeHost: CarOverlayHost? get() = null
    fun canDrawOverlays(): Boolean
    fun show(
        host: CarOverlayHost,
        onShortcut: (CarWorkspaceShortcutSlot) -> Unit,
    ): CarOverlayShowResult
    fun hide()
    fun collapse()
    fun setActionsEnabled(enabled: Boolean)
    fun updateShortcuts(shortcuts: List<CarFloatingWorkspaceShortcut>)
    fun dispose()
}

fun interface CarFloatingDockWindowFactory {
    fun create(
        host: CarOverlayHost,
        onShortcut: (CarWorkspaceShortcutSlot) -> Unit,
        onDockStateChanged: () -> Unit,
    ): CarFloatingDockWindow?
}

enum class CarFloatingDockState { Collapsed, Expanded }

fun interface CarOverlayDisplayRegistration {
    fun unregister()
}

fun interface CarOverlayDisplayEvents {
    fun register(onDisplayRemoved: (Int) -> Unit): CarOverlayDisplayRegistration
}

sealed interface CarOverlaySessionState {
    data object NotShown : CarOverlaySessionState
    data class Shown(
        val displayId: Int,
        val dockState: CarFloatingDockState,
    ) : CarOverlaySessionState
    data object Disposed : CarOverlaySessionState
}

sealed interface CarOverlayShowResult {
    data class Shown(val displayId: Int) : CarOverlayShowResult
    data class AlreadyShown(val displayId: Int) : CarOverlayShowResult
    data object PermissionDenied : CarOverlayShowResult
    data object DisplayUnavailable : CarOverlayShowResult
    data object WindowUnavailable : CarOverlayShowResult
    data object SessionDisposed : CarOverlayShowResult
}

class CarOverlaySession(
    private val permissionChecker: CarOverlayPermissionChecker,
    private val windowFactory: CarFloatingDockWindowFactory,
    private val displayEvents: CarOverlayDisplayEvents,
) : CarFloatingDockSession {
    private var window: CarFloatingDockWindow? = null
    private var displayRegistration: CarOverlayDisplayRegistration? = null
    private var disposed = false
    private val mutableStateChanges = MutableStateFlow<CarOverlaySessionState>(
        CarOverlaySessionState.NotShown,
    )

    override val stateChanges: StateFlow<CarOverlaySessionState> = mutableStateChanges.asStateFlow()

    override val activeHost: CarOverlayHost?
        @Synchronized get() = host

    private var host: CarOverlayHost? = null

    override val state: CarOverlaySessionState
        @Synchronized get() = when {
            disposed -> CarOverlaySessionState.Disposed
            window != null -> CarOverlaySessionState.Shown(
                requireNotNull(window).display.id,
                requireNotNull(window).dockState,
            )
            else -> CarOverlaySessionState.NotShown
        }

    override fun canDrawOverlays(): Boolean = permissionChecker.canDrawOverlays()

    @Synchronized
    override fun show(
        host: CarOverlayHost,
        onShortcut: (CarWorkspaceShortcutSlot) -> Unit,
    ): CarOverlayShowResult {
        if (disposed) return CarOverlayShowResult.SessionDisposed
        if (!permissionChecker.canDrawOverlays()) {
            removeWindow()
            return CarOverlayShowResult.PermissionDenied
        }
        window?.let { return CarOverlayShowResult.AlreadyShown(it.display.id) }

        val suppliedHost = host
            .takeIf { it.display.id != 0 }
            ?: return CarOverlayShowResult.DisplayUnavailable
        val createdWindow = windowFactory.create(suppliedHost, onShortcut, ::publishState)
            ?: return CarOverlayShowResult.WindowUnavailable

        window = createdWindow
        this.host = suppliedHost
        displayRegistration = displayEvents.register(::onDisplayRemoved)
        publishState()
        return CarOverlayShowResult.Shown(suppliedHost.display.id)
    }

    @Synchronized
    override fun hide() {
        removeWindow()
    }

    @Synchronized
    override fun collapse() {
        val shownWindow = window ?: return
        if (shownWindow.dockState == CarFloatingDockState.Collapsed) return
        shownWindow.collapse()
    }

    @Synchronized
    override fun setActionsEnabled(enabled: Boolean) {
        window?.setActionsEnabled(enabled)
    }

    @Synchronized
    override fun updateShortcuts(shortcuts: List<CarFloatingWorkspaceShortcut>) {
        window?.updateShortcuts(shortcuts)
    }

    @Synchronized
    override fun dispose() {
        if (disposed) return
        removeWindow()
        disposed = true
        publishState()
    }

    @Synchronized
    internal fun performHandleClickForTest(): Boolean = window?.performHandleClickForTest() == true

    @Synchronized
    internal fun performShortcutClickForTest(slot: CarWorkspaceShortcutSlot): Boolean =
        window?.performShortcutClickForTest(slot) == true

    @Synchronized
    internal fun performCollapseClickForTest(): Boolean = window?.performCollapseClickForTest() == true

    @Synchronized
    internal fun positionForTest(): CarFloatingDockPosition? = window?.positionForTest()

    @Synchronized
    private fun onDisplayRemoved(displayId: Int) {
        if (window?.display?.id == displayId) removeWindow()
    }

    private fun removeWindow() {
        displayRegistration?.unregister()
        displayRegistration = null
        window?.remove()
        window = null
        host = null
        publishState()
    }

    private fun publishState() {
        mutableStateChanges.value = state
    }
}
