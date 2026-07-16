package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.Composable
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.ui.toComposePlacement

@Composable
fun WorkspaceSnapshot(
    canvas: WorkspaceCanvas,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    includeAccessibilitySummary: Boolean = true,
) {
    val shape = DesignerShapes.Workspace
    val summary = canvas.accessibilitySummary()
    val accessibilityModifier = if (includeAccessibilitySummary) {
        Modifier.clearAndSetSemantics { contentDescription = summary }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Layout(
        content = {
            canvas.cells.forEach { cell ->
                WorkspaceSnapshotCell(cell = cell, showLabels = showLabels)
            }
        },
        modifier = modifier
            .aspectRatio(Dimensions.WorkspaceAspectRatio)
            .clip(shape)
            .background(DesignerColors.WorkspaceBackground)
            .border(Dimensions.WorkspaceBorderWidth, DesignerColors.Divider, shape)
            .then(accessibilityModifier),
    ) { measurables, constraints ->
        val width = constraints.maxWidth.coerceAtLeast(1)
        val height = constraints.maxHeight.coerceAtLeast(1)
        val placements = canvas.cells.map { it.bounds.toComposePlacement(width.toFloat(), height.toFloat()) }
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
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val placement = placements[index]
                placeable.placeRelative(placement.x, placement.y)
            }
        }
    }
}
