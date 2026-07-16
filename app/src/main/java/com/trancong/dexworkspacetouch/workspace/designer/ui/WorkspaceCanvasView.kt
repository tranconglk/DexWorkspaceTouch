package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas

@Composable
fun WorkspaceCanvasView(
    canvas: WorkspaceCanvas,
    selectedCellId: String?,
    onCellSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canvasShape = RoundedCornerShape(16.dp)
    Layout(
        content = {
            canvas.cells.forEach { cell ->
                WorkspaceCellView(
                    cell = cell,
                    selected = cell.id == selectedCellId,
                    onClick = { onCellSelected(cell.id) },
                )
            }
        },
        modifier = modifier
            .aspectRatio(16f / 10f)
            .clip(canvasShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.dp, MaterialTheme.colorScheme.outline, canvasShape),
    ) { measurables, constraints ->
        val canvasWidth = constraints.maxWidth.coerceAtLeast(1)
        val canvasHeight = constraints.maxHeight.coerceAtLeast(1)
        val placements = canvas.cells.map { cell ->
            cell.bounds.toComposePlacement(canvasWidth.toFloat(), canvasHeight.toFloat())
        }
        val placeables = measurables.mapIndexed { index, measurable ->
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
        layout(canvasWidth, canvasHeight) {
            placeables.forEachIndexed { index, placeable ->
                val placement = placements[index]
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
            onCellSelected = {},
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
            onCellSelected = {},
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
            onCellSelected = {},
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
