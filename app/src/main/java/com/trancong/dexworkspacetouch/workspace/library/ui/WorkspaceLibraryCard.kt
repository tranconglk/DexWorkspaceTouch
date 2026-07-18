package com.trancong.dexworkspacetouch.workspace.library.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshot

@Composable
fun WorkspaceLibraryCard(
    workspace: WorkspaceLibraryItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onManage: () -> Unit,
    canDelete: Boolean,
    modifier: Modifier = Modifier,
    openEnabled: Boolean = true,
    duplicateEnabled: Boolean = true,
) {
    Card(
        onClick = onSelect,
        modifier = modifier
            .heightIn(min = Dimensions.WorkspaceCardMinHeight)
            .semantics {
                contentDescription = "Workspace ${workspace.name}, ${workspace.appCount} ứng dụng."
                if (selected) stateDescription = "Đang được chọn."
            },
        shape = DesignerShapes.Workspace,
        border = BorderStroke(
            if (selected) Dimensions.SelectionBorderWidth else Dimensions.CellBorderWidth,
            if (selected) DesignerColors.Selection else DesignerColors.CellBorder,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignerElevation.SnapshotCard),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            WorkspaceSnapshot(
                canvas = workspace.canvas,
                modifier = Modifier.fillMaxWidth(),
                includeAccessibilitySummary = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${workspace.appCount} ứng dụng", style = MaterialTheme.typography.bodySmall)
                Text("Cập nhật #${workspace.modifiedSequence}", style = MaterialTheme.typography.bodySmall)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val showDirectManagementActions = maxWidth >= Dimensions.WorkspaceCardWideActionsWidth
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    ) {
                        OutlinedButton(
                            onClick = onOpen,
                            enabled = openEnabled,
                            modifier = Modifier.weight(1f).height(TouchTargets.SecondaryButton),
                        ) { Text("Mở") }
                        Button(
                            onClick = onEdit,
                            modifier = Modifier.weight(1f).height(TouchTargets.SecondaryButton),
                        ) { Text("Sửa") }
                    }
                    if (selected) {
                        if (showDirectManagementActions) {
                            OutlinedButton(
                                onClick = onDuplicate,
                                enabled = duplicateEnabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(TouchTargets.SecondaryButton)
                                    .semantics {
                                        contentDescription = "Nhân bản workspace ${workspace.name}."
                                    },
                            ) { Text("Nhân bản") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                            ) {
                                OutlinedButton(
                                    onClick = onRename,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(TouchTargets.SecondaryButton)
                                        .semantics {
                                            contentDescription = "Đổi tên workspace ${workspace.name}."
                                        },
                                ) { Text("Đổi tên") }
                                OutlinedButton(
                                    onClick = onDelete,
                                    enabled = canDelete,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error,
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(TouchTargets.SecondaryButton)
                                        .semantics {
                                            contentDescription = "Xóa workspace ${workspace.name}."
                                        },
                                ) { Text("Xóa") }
                            }
                        } else {
                            OutlinedButton(
                                onClick = onManage,
                                modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                            ) { Text("Quản lý") }
                        }
                    }
                }
            }
        }
    }
}
