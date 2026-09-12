package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcuts
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceNormalizedBounds
import com.trancong.dexworkspacetouch.feature.car.CarWorkspacePreview
import com.trancong.dexworkspacetouch.feature.car.CarWorkspacePreviewCell
import com.trancong.dexworkspacetouch.feature.car.toCarWorkspacePreview
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace

sealed interface CarFloatingWorkspaceShortcutState {
    data class Configured(val workspaceId: String) : CarFloatingWorkspaceShortcutState
    data object Unconfigured : CarFloatingWorkspaceShortcutState
    data class Unavailable(val workspaceId: String) : CarFloatingWorkspaceShortcutState
}

data class CarFloatingWorkspaceShortcut(
    val slot: CarWorkspaceShortcutSlot,
    val accessibilityLabel: String,
    val state: CarFloatingWorkspaceShortcutState,
    val preview: CarFloatingWorkspacePreview? = null,
) {
    val enabled: Boolean get() = state is CarFloatingWorkspaceShortcutState.Configured

    init {
        require((state is CarFloatingWorkspaceShortcutState.Configured) == (preview != null)) {
            "Only configured shortcuts have a workspace preview"
        }
    }
}

typealias CarFloatingWorkspacePreview = CarWorkspacePreview
typealias CarFloatingWorkspacePreviewCell = CarWorkspacePreviewCell
typealias CarFloatingNormalizedBounds = CarWorkspaceNormalizedBounds

fun Workspace.toCarFloatingPreview(): CarFloatingWorkspacePreview = toCarWorkspacePreview()

object CarFloatingDockGrid {
    const val Columns = 2
    const val TileHeightDp = 68
    const val HeaderHeightDp = 56
    fun rows(itemCount: Int): Int = (itemCount + Columns - 1) / Columns
    fun expandedHeightDp(itemCount: Int): Int =
        rows(itemCount) * TileHeightDp + HeaderHeightDp
    fun row(slot: CarWorkspaceShortcutSlot): Int = slot.ordinal / Columns
    fun column(slot: CarWorkspaceShortcutSlot): Int = slot.ordinal % Columns
}

internal object CarFloatingDockVisual {
    const val CollapsedSizeDp = 72
    const val ExpandedWidthDp = 360
    const val OpenDescription = "Open Car Dock"
    const val CollapseDescription = "Car Dock. Collapse Floating Dock"
}

fun resolveFloatingWorkspaceShortcuts(
    shortcuts: CarWorkspaceShortcuts,
    workspacesById: Map<String, Workspace>,
    visibleSlotCount: Int,
): List<CarFloatingWorkspaceShortcut> = CarWorkspaceShortcutSlot.entries
    .take(com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutCapacity.normalizeVisibleCount(visibleSlotCount))
    .map { slot ->
    val workspaceId = shortcuts[slot].workspaceId
    val workspace = workspaceId?.let(workspacesById::get)
    when {
        workspaceId == null -> CarFloatingWorkspaceShortcut(
            slot,
            "${slot.displayLabel} — Not configured",
            CarFloatingWorkspaceShortcutState.Unconfigured,
        )
        workspace == null -> CarFloatingWorkspaceShortcut(
            slot,
            "${slot.displayLabel} — Workspace unavailable",
            CarFloatingWorkspaceShortcutState.Unavailable(workspaceId),
        )
        else -> CarFloatingWorkspaceShortcut(
            slot,
            "${slot.displayLabel} — ${workspace.name}",
            CarFloatingWorkspaceShortcutState.Configured(workspaceId),
            workspace.toCarFloatingPreview(),
        )
    }
}
