package com.trancong.dexworkspacetouch.workspace.library.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.zIndex
import com.trancong.dexworkspacetouch.ui.design.DesignerAnimation
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.ui.design.ZLayers
import com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshot
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader

@Composable
fun WorkspaceLibraryCard(
    workspace: WorkspaceLibraryItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onPinToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onManage: () -> Unit,
    canDelete: Boolean,
    modifier: Modifier = Modifier,
    openEnabled: Boolean = true,
    duplicateEnabled: Boolean = true,
    pinEnabled: Boolean = true,
    onExport: () -> Unit = {},
    appIconLoader: AppIconLoader? = null,
    nowEpochMillis: Long = 0L,
) {
    val metadata = workspace.canvas.cardMetadata()
    val updatedText = WorkspaceRelativeTimeFormatter.format(workspace.updatedAtEpochMillis, nowEpochMillis)
    val cardDescription = buildString {
        append("Workspace ${workspace.name}, ${metadata.cellCount} ô, ${metadata.assignedCount} ứng dụng, cập nhật $updatedText.")
        if (workspace.isPinned) append(" Đã ghim.")
    }
    Box(modifier = modifier.heightIn(min = Dimensions.WorkspaceCardMinHeight)) {
        Card(
            onClick = onSelect,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = cardDescription
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
                    modifier = Modifier.padding(end = TouchTargets.SecondaryButton + Spacing.S),
                )
                WorkspaceSnapshot(
                    canvas = workspace.canvas,
                    modifier = Modifier.fillMaxWidth(),
                    includeAccessibilitySummary = false,
                    appIconLoader = appIconLoader,
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                    Text(
                        metadata.compactText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.heightIn(min = Dimensions.WorkspaceCardMetadataMinHeight),
                    )
                    Text(
                        updatedText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = Dimensions.WorkspaceCardUpdatedTextMinHeight),
                    )
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
                                onClick = onExport,
                                modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                            ) { Text("Xuất") }
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
        IconButton(
            onClick = onPinToggle,
            enabled = pinEnabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = Spacing.S, end = Spacing.S)
                .size(TouchTargets.SecondaryButton)
                .zIndex(ZLayers.ActionOverlay)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .semantics {
                    contentDescription = if (workspace.isPinned) {
                        "Bỏ ghim workspace ${workspace.name}."
                    } else "Ghim workspace ${workspace.name}."
                },
        ) {
            Crossfade(
                targetState = workspace.isPinned,
                animationSpec = tween(DesignerAnimation.FastDurationMillis),
                label = "workspacePinState",
            ) { pinned ->
                WorkspaceBookmarkIcon(pinned)
            }
        }
    }
}

@Composable
private fun WorkspaceBookmarkIcon(pinned: Boolean) {
    val color = if (pinned) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier = Modifier.size(Dimensions.WorkspacePinIconSize)) {
        val scale = size.minDimension / 24f
        val path = Path().apply {
            moveTo(6f * scale, 3f * scale)
            lineTo(18f * scale, 3f * scale)
            lineTo(18f * scale, 21f * scale)
            lineTo(12f * scale, 17f * scale)
            lineTo(6f * scale, 21f * scale)
            close()
        }
        if (pinned) drawPath(path, color)
        else drawPath(path, color, style = Stroke(width = 2f * scale))
    }
}
