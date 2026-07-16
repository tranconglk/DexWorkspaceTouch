package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.InteractionZones
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

@Composable
fun WorkspaceCellView(
    cell: WorkspaceCell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = cell.app?.let { "${it.label}. Chạm để chọn ô." }
        ?: "Ô trống. Chạm để chọn ô."
    val border = if (selected) {
        BorderStroke(Dimensions.SelectionBorderWidth, DesignerColors.Selection)
    } else {
        BorderStroke(Dimensions.CellBorderWidth, DesignerColors.CellBorder)
    }

    Card(
        modifier = modifier
            .clip(DesignerShapes.Cell)
            .semantics(mergeDescendants = false) {
                contentDescription = description
                if (selected) stateDescription = "Đang được chọn"
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = DesignerShapes.Cell,
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                DesignerColors.SelectedCellBackground
            } else {
                DesignerColors.CellBackground
            },
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = InteractionZones.DeadZone,
                        top = InteractionZones.DeadZone,
                        end = InteractionZones.DeadZone,
                        bottom = if (selected) {
                            Dimensions.SelectedCellContentBottomPadding
                        } else {
                            InteractionZones.DeadZone
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (cell.app == null) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineLarge,
                        color = DesignerColors.Accent,
                    )
                    Text(
                        text = "Chạm để chọn ứng dụng",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Text(
                        text = cell.app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun EmptyWorkspaceCellPreview() {
    MaterialTheme {
        WorkspaceCellView(
            cell = WorkspaceCell("empty", NormalizedBounds.FullCanvas),
            selected = false,
            onClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun AssignedWorkspaceCellPreview() {
    MaterialTheme {
        WorkspaceCellView(
            cell = WorkspaceCell(
                id = "notes",
                bounds = NormalizedBounds.FullCanvas,
                app = AssignedApp("com.example.notes", "com.example.notes.MainActivity", "Ghi chú"),
            ),
            selected = true,
            onClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
