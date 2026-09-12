package com.trancong.dexworkspacetouch.feature.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets

data class CarWorkspaceOption(
    val id: String,
    val name: String,
    val appCount: Int,
    val preview: CarWorkspacePreview? = null,
)

data class CarWorkspaceShortcutRow(
    val slot: CarWorkspaceShortcutSlot,
    val workspaceId: String?,
    val statusText: String,
    val appCount: Int? = null,
    val status: CarWorkspaceShortcutStatus,
    val preview: CarWorkspacePreview? = null,
)

val CarWorkspaceShortcutRow.dashboardTitle: String
    get() = statusText

val CarWorkspaceShortcutRow.dashboardAccessibilityLabel: String
    get() = when (status) {
        CarWorkspaceShortcutStatus.Configured -> "Mở Workspace $statusText"
        CarWorkspaceShortcutStatus.Unconfigured -> "${slot.displayLabel}, chưa cấu hình"
        CarWorkspaceShortcutStatus.Unavailable -> "${slot.displayLabel}, Workspace không khả dụng"
    }

enum class CarWorkspaceShortcutStatus { Configured, Unconfigured, Unavailable }

fun CarWorkspaceOption.isSelected(selectedWorkspaceId: String?): Boolean =
    id == selectedWorkspaceId

fun resolveCarWorkspaceShortcutRows(
    shortcuts: CarWorkspaceShortcuts,
    workspaces: List<CarWorkspaceOption>,
    visibleSlotCount: Int = CarWorkspaceShortcutCapacity.DefaultVisible,
): List<CarWorkspaceShortcutRow> {
    val byId = workspaces.associateBy(CarWorkspaceOption::id)
    return CarWorkspaceShortcutSlot.entries
        .take(CarWorkspaceShortcutCapacity.normalizeVisibleCount(visibleSlotCount))
        .map { slot ->
        val workspaceId = shortcuts[slot].workspaceId
        CarWorkspaceShortcutRow(
            slot = slot,
            workspaceId = workspaceId,
            statusText = when {
                workspaceId == null -> "Chưa cấu hình"
                byId[workspaceId] != null -> byId.getValue(workspaceId).name
                else -> "Workspace không khả dụng"
            },
            appCount = workspaceId?.let(byId::get)?.appCount,
            preview = workspaceId?.let(byId::get)?.preview,
            status = when {
                workspaceId == null -> CarWorkspaceShortcutStatus.Unconfigured
                byId[workspaceId] != null -> CarWorkspaceShortcutStatus.Configured
                else -> CarWorkspaceShortcutStatus.Unavailable
            },
        )
    }
}

@Composable
fun CarWorkspaceShortcutSection(
    rows: List<CarWorkspaceShortcutRow>,
    onSlotSelected: (CarWorkspaceShortcutSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text("Lối tắt Workspace", style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            Surface(
                onClick = { onSlotSelected(row.slot) },
                modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.PrimaryButton)
                    .semantics { contentDescription = "${row.slot.displayLabel}, ${row.statusText}" },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.M, vertical = Spacing.S),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.slot.displayLabel, style = MaterialTheme.typography.labelMedium)
                        Text(
                            row.statusText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(">")
                }
            }
        }
    }
}

@Composable
fun CarWorkspaceSelectorDialog(
    slot: CarWorkspaceShortcutSlot,
    selectedWorkspaceId: String?,
    workspaces: List<CarWorkspaceOption>,
    onWorkspaceSelected: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn Workspace cho ${slot.displayLabel}") },
        text = {
            if (workspaces.isEmpty()) {
                Text("Không có Workspace khả dụng")
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = TouchTargets.SecondaryButton * 6),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    items(workspaces, key = CarWorkspaceOption::id) { workspace ->
                        OutlinedButton(
                            onClick = { onWorkspaceSelected(workspace.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = TouchTargets.SecondaryButton),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(workspace.name)
                                Text(
                                    "${workspace.appCount} ứng dụng",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (workspace.isSelected(selectedWorkspaceId)) Text("Đang chọn")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text("Đóng") }
        },
        dismissButton = if (selectedWorkspaceId != null) {
            {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Xóa chọn") }
            }
        } else {
            null
        },
    )
}
