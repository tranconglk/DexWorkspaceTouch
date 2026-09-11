package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcuts
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
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

class CarFloatingWorkspacePreview(cells: List<CarFloatingWorkspacePreviewCell>) {
    val cells: List<CarFloatingWorkspacePreviewCell> = java.util.Collections.unmodifiableList(cells.toList())

    override fun equals(other: Any?): Boolean =
        other is CarFloatingWorkspacePreview && cells == other.cells

    override fun hashCode(): Int = cells.hashCode()

    override fun toString(): String = "CarFloatingWorkspacePreview(cells=$cells)"
}

data class CarFloatingWorkspacePreviewCell(
    val bounds: CarFloatingNormalizedBounds,
    val appIdentity: AppIdentity?,
)

class CarFloatingNormalizedBounds private constructor(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    override fun equals(other: Any?): Boolean = other is CarFloatingNormalizedBounds &&
        left == other.left && top == other.top && right == other.right && bottom == other.bottom

    override fun hashCode(): Int = arrayOf(left, top, right, bottom).contentHashCode()

    override fun toString(): String =
        "CarFloatingNormalizedBounds(left=$left, top=$top, right=$right, bottom=$bottom)"

    companion object {
        fun normalized(left: Float, top: Float, right: Float, bottom: Float): CarFloatingNormalizedBounds? {
            if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) return null
            val clampedLeft = left.coerceIn(0f, 1f)
            val clampedTop = top.coerceIn(0f, 1f)
            val clampedRight = right.coerceIn(0f, 1f)
            val clampedBottom = bottom.coerceIn(0f, 1f)
            if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return null
            return CarFloatingNormalizedBounds(clampedLeft, clampedTop, clampedRight, clampedBottom)
        }
    }
}

fun Workspace.toCarFloatingPreview(): CarFloatingWorkspacePreview = CarFloatingWorkspacePreview(
    cells = canvas.cells.mapNotNull { cell ->
        CarFloatingNormalizedBounds.normalized(
            cell.bounds.left,
            cell.bounds.top,
            cell.bounds.right,
            cell.bounds.bottom,
        )?.let { bounds ->
            CarFloatingWorkspacePreviewCell(
                bounds = bounds,
                appIdentity = cell.app?.let { AppIdentity(it.packageName, it.activityName) },
            )
        }
    }.toList(),
)

object CarFloatingDockGrid {
    const val Columns = 2
    const val TileHeightDp = 68
    const val CollapseHeightDp = 56
    fun rows(itemCount: Int): Int = (itemCount + Columns - 1) / Columns
    fun expandedHeightDp(itemCount: Int): Int =
        rows(itemCount) * TileHeightDp + CollapseHeightDp
    fun row(slot: CarWorkspaceShortcutSlot): Int = slot.ordinal / Columns
    fun column(slot: CarWorkspaceShortcutSlot): Int = slot.ordinal % Columns
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
