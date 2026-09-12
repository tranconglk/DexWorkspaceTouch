package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.InteractionZones
import com.trancong.dexworkspacetouch.ui.design.ZLayers
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceDivider
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.dividers
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import kotlin.math.roundToInt

@Composable
fun WorkspaceCanvasView(
    canvas: WorkspaceCanvas,
    selectedCellId: String?,
    selectedDividerId: String?,
    onCellActivated: (String) -> Unit,
    onDividerSelected: (String) -> Unit,
    onDividerDragStart: (String) -> Unit,
    onDividerDragRatio: (Float) -> Unit,
    onDividerDragEnd: () -> Unit,
    onDividerDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
    appIconLoader: AppIconLoader? = null,
) {
    val canvasShape = DesignerShapes.Workspace
    val dividers = canvas.dividers()
    val dividerHitSize = with(LocalDensity.current) { InteractionZones.DividerHitArea.roundToPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Layout(
        content = {
            canvas.cells.forEach { cell ->
                WorkspaceCellView(
                    cell = cell,
                    selected = cell.id == selectedCellId,
                    onClick = { onCellActivated(cell.id) },
                    appIconLoader = appIconLoader,
                    modifier = Modifier.zIndex(ZLayers.Cell),
                )
            }
            dividers.forEach { divider ->
                WorkspaceDividerView(
                    divider = divider,
                    selected = divider.id == selectedDividerId,
                    onClick = { onDividerSelected(divider.id) },
                    canvasWidthPx = canvasSize.width.toFloat(),
                    canvasHeightPx = canvasSize.height.toFloat(),
                    onDragStart = { onDividerDragStart(divider.id) },
                    onDragRatio = onDividerDragRatio,
                    onDragEnd = onDividerDragEnd,
                    onDragCancel = onDividerDragCancel,
                )
            }
        },
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .aspectRatio(Dimensions.WorkspaceAspectRatio)
            .clip(canvasShape)
            .background(DesignerColors.WorkspaceBackground)
            .border(Dimensions.WorkspaceBorderWidth, DesignerColors.Divider, canvasShape),
    ) { measurables, constraints ->
        val canvasWidth = constraints.maxWidth.coerceAtLeast(1)
        val canvasHeight = constraints.maxHeight.coerceAtLeast(1)
        val placements = canvas.cells.map { cell ->
            cell.bounds.toComposePlacement(canvasWidth.toFloat(), canvasHeight.toFloat())
        }
        val cellPlaceables = measurables.take(canvas.cells.size).mapIndexed { index, measurable ->
            val placement = placements[index]
            measurable.measure(
                constraints.copy(
                    minWidth = placement.width,
                    maxWidth = placement.width,
                    minHeight = placement.height,
                    maxHeight = placement.height,
                ),
            )
        }
        val dividerPlacements = dividers.map { divider ->
            divider.toPlacement(canvasWidth, canvasHeight, dividerHitSize)
        }
        val dividerPlaceables = measurables
            .drop(canvas.cells.size)
            .take(dividers.size)
            .mapIndexed { index, measurable ->
                val placement = dividerPlacements[index]
                measurable.measure(
                    constraints.copy(
                        minWidth = placement.width,
                        maxWidth = placement.width,
                        minHeight = placement.height,
                        maxHeight = placement.height,
                    ),
                )
            }
        layout(canvasWidth, canvasHeight) {
            cellPlaceables.forEachIndexed { index, placeable ->
                val placement = placements[index]
                placeable.placeRelative(placement.x, placement.y)
            }
            dividerPlaceables.forEachIndexed { index, placeable ->
                val placement = dividerPlacements[index]
                placeable.placeRelative(placement.x, placement.y)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 640, heightDp = 440)
@Composable
private fun SingleCellCanvasPreview() {
    MaterialTheme {
        WorkspaceCanvasView(
            canvas = WorkspaceCanvasPreviewData.singleCellCanvas(),
            selectedCellId = null,
            selectedDividerId = null,
            onCellActivated = {},
            onDividerSelected = {},
            onDividerDragStart = {},
            onDividerDragRatio = {},
            onDividerDragEnd = {},
            onDividerDragCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, widthDp = 640, heightDp = 440)
@Composable
private fun TwoCellCanvasPreview() {
    MaterialTheme {
        WorkspaceCanvasView(
            canvas = WorkspaceCanvasPreviewData.twoVerticalCellsCanvas(),
            selectedCellId = null,
            selectedDividerId = null,
            onCellActivated = {},
            onDividerSelected = {},
            onDividerDragStart = {},
            onDividerDragRatio = {},
            onDividerDragEnd = {},
            onDividerDragCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview(showBackground = true, widthDp = 640, heightDp = 440)
@Composable
private fun ThreeCellSelectedCanvasPreview() {
    MaterialTheme {
        WorkspaceCanvasView(
            canvas = WorkspaceCanvasPreviewData.threeCellsCanvas(),
            selectedCellId = "top-right",
            selectedDividerId = null,
            onCellActivated = {},
            onDividerSelected = {},
            onDividerDragStart = {},
            onDividerDragRatio = {},
            onDividerDragEnd = {},
            onDividerDragCancel = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun WorkspaceDivider.toPlacement(
    canvasWidth: Int,
    canvasHeight: Int,
    requestedHitSize: Int,
): ComposePlacement = when (direction) {
    SplitDirection.VERTICAL -> {
        val hitSize = requestedHitSize.coerceAtMost(canvasWidth).coerceAtLeast(1)
        val y = (start * canvasHeight).roundToInt().coerceIn(0, canvasHeight - 1)
        val bottom = (end * canvasHeight).roundToInt().coerceIn(y + 1, canvasHeight)
        val x = (position * canvasWidth).roundToInt()
            .minus(hitSize / 2)
            .coerceIn(0, canvasWidth - hitSize)
        ComposePlacement(x, y, hitSize, bottom - y)
    }
    SplitDirection.HORIZONTAL -> {
        val hitSize = requestedHitSize.coerceAtMost(canvasHeight).coerceAtLeast(1)
        val x = (start * canvasWidth).roundToInt().coerceIn(0, canvasWidth - 1)
        val right = (end * canvasWidth).roundToInt().coerceIn(x + 1, canvasWidth)
        val y = (position * canvasHeight).roundToInt()
            .minus(hitSize / 2)
            .coerceIn(0, canvasHeight - hitSize)
        ComposePlacement(x, y, right - x, hitSize)
    }
}
