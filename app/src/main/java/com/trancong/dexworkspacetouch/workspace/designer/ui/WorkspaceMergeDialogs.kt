package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceMergeCandidate
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceMergeDirection

@Composable
fun WorkspaceMergeTargetDialog(
    canvas: WorkspaceCanvas,
    candidates: List<WorkspaceMergeCandidate>,
    onTargetSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn ô để gộp") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                candidates.forEach { candidate ->
                    val target = canvas.cells.first { it.id == candidate.targetCellId }
                    val targetName = target.app?.label ?: "ô trống"
                    val direction = candidate.direction.label
                    OutlinedButton(
                        onClick = { onTargetSelected(candidate.targetCellId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = TouchTargets.SecondaryButton)
                            .semantics {
                                contentDescription = "Gộp $direction với ô $targetName."
                            },
                    ) {
                        Text("Gộp $direction — ${if (target.app == null) "ô trống" else "giữ $targetName"}")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text("Hủy") }
        },
    )
}

@Composable
fun WorkspaceMergeConfirmationDialog(
    sourceLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xác nhận gộp ô") },
        text = {
            Text(
                "Gộp hai ô sẽ bỏ ứng dụng $sourceLabel khỏi bố cục.",
                modifier = Modifier.semantics {
                    contentDescription = "Gộp sẽ bỏ ứng dụng $sourceLabel."
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text("Gộp") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text("Hủy") }
        },
    )
}

private val WorkspaceMergeDirection.label: String
    get() = when (this) {
        WorkspaceMergeDirection.LEFT -> "sang trái"
        WorkspaceMergeDirection.RIGHT -> "sang phải"
        WorkspaceMergeDirection.UP -> "lên trên"
        WorkspaceMergeDirection.DOWN -> "xuống dưới"
    }
