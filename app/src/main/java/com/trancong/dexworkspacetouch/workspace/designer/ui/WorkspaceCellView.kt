package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell

@Composable
fun WorkspaceCellView(
    cell: WorkspaceCell,
    selected: Boolean,
    onClick: () -> Unit,
    cellCount: Int,
    onChooseApp: () -> Unit,
    onSplit: (SplitDirection) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = cell.app?.let { "${it.label}. Chạm để chọn ô." }
        ?: "Ô trống. Chạm để chọn ô."
    val border = if (selected) {
        BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .semantics(mergeDescendants = false) {
                contentDescription = description
                if (selected) stateDescription = "Đang được chọn"
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 12.dp,
                        top = 12.dp,
                        end = 12.dp,
                        bottom = if (selected) 72.dp else 12.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (cell.app == null) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary,
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
            if (selected) {
                val maximumReached = cellCount >= 4
                val canSplitHorizontal = !maximumReached && cell.bounds.height / 2f >= 0.2f
                val canSplitVertical = !maximumReached && cell.bounds.width / 2f >= 0.2f
                WorkspaceCellActionOverlay(
                    cell = cell,
                    canSplitHorizontal = canSplitHorizontal,
                    canSplitVertical = canSplitVertical,
                    onChooseApp = onChooseApp,
                    onSplitHorizontal = { onSplit(SplitDirection.HORIZONTAL) },
                    onSplitVertical = { onSplit(SplitDirection.VERTICAL) },
                    onClearSelection = onClearSelection,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
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
            cellCount = 1,
            onChooseApp = {},
            onSplit = {},
            onClearSelection = {},
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
            cellCount = 1,
            onChooseApp = {},
            onSplit = {},
            onClearSelection = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
