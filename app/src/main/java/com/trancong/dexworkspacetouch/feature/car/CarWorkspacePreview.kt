package com.trancong.dexworkspacetouch.feature.car

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace

/** Shared normalized presentation data used by both CarScreen and the Floating Dock. */
class CarWorkspacePreview(cells: List<CarWorkspacePreviewCell>) {
    val cells: List<CarWorkspacePreviewCell> =
        java.util.Collections.unmodifiableList(cells.toList())

    override fun equals(other: Any?): Boolean =
        other is CarWorkspacePreview && cells == other.cells

    override fun hashCode(): Int = cells.hashCode()

    override fun toString(): String = "CarWorkspacePreview(cells=$cells)"
}

data class CarWorkspacePreviewCell(
    val bounds: CarWorkspaceNormalizedBounds,
    val appIdentity: AppIdentity?,
)

class CarWorkspaceNormalizedBounds private constructor(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    override fun equals(other: Any?): Boolean = other is CarWorkspaceNormalizedBounds &&
        left == other.left && top == other.top && right == other.right && bottom == other.bottom

    override fun hashCode(): Int = arrayOf(left, top, right, bottom).contentHashCode()

    override fun toString(): String =
        "CarWorkspaceNormalizedBounds(left=$left, top=$top, right=$right, bottom=$bottom)"

    companion object {
        fun normalized(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ): CarWorkspaceNormalizedBounds? {
            if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
                return null
            }
            val clampedLeft = left.coerceIn(0f, 1f)
            val clampedTop = top.coerceIn(0f, 1f)
            val clampedRight = right.coerceIn(0f, 1f)
            val clampedBottom = bottom.coerceIn(0f, 1f)
            if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) return null
            return CarWorkspaceNormalizedBounds(
                clampedLeft,
                clampedTop,
                clampedRight,
                clampedBottom,
            )
        }
    }
}

fun Workspace.toCarWorkspacePreview(): CarWorkspacePreview = CarWorkspacePreview(
    cells = canvas.cells.mapNotNull { cell ->
        CarWorkspaceNormalizedBounds.normalized(
            cell.bounds.left,
            cell.bounds.top,
            cell.bounds.right,
            cell.bounds.bottom,
        )?.let { bounds ->
            CarWorkspacePreviewCell(
                bounds = bounds,
                appIdentity = cell.app?.let { AppIdentity(it.packageName, it.activityName) },
            )
        }
    }.toList(),
)
