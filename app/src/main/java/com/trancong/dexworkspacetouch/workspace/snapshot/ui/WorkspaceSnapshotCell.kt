package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

@Composable
internal fun WorkspaceSnapshotCell(
    cell: WorkspaceCell,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.clip(DesignerShapes.Cell),
        shape = DesignerShapes.Cell,
        border = BorderStroke(Dimensions.CellBorderWidth, DesignerColors.CellBorder),
        colors = CardDefaults.cardColors(containerColor = DesignerColors.CellBackground),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val hasLabelSpace = maxWidth >= Dimensions.SnapshotLabelMinWidth &&
                maxHeight >= Dimensions.SnapshotLabelMinHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                SnapshotIcon(cell)
                if (showLabels && hasLabelSpace) {
                    Text(
                        text = cell.app?.label ?: "Trống",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (cell.app == null) null else FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SnapshotIcon(cell: WorkspaceCell) {
    Surface(
        modifier = Modifier.size(Dimensions.AppIconSize),
        shape = DesignerShapes.DividerFeedback,
        color = if (cell.app == null) DesignerColors.WorkspaceBackground else DesignerColors.SelectedCellBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = cell.app?.label?.firstOrNull()?.uppercase() ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = DesignerColors.Accent,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
