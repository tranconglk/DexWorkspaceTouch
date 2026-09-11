package com.trancong.dexworkspacetouch.feature.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets

data class CarWorkspaceOption(
    val id: String,
    val name: String,
    val appCount: Int,
)

data class CarWorkspaceShortcutRow(
    val slot: CarWorkspaceShortcutSlot,
    val workspaceId: String?,
    val statusText: String,
    val appCount: Int? = null,
    val status: CarWorkspaceShortcutStatus,
)

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
                workspaceId == null -> "Not configured"
                byId[workspaceId] != null -> byId.getValue(workspaceId).name
                else -> "Workspace unavailable"
            },
            appCount = workspaceId?.let(byId::get)?.appCount,
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
        Text("Workspace shortcuts", style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            OutlinedButton(
                onClick = { onSlotSelected(row.slot) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TouchTargets.SecondaryButton),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.slot.displayLabel)
                    Text(
                        row.statusText,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(">")
                }
            }
        }
    }
}

@Composable
fun CarWorkspaceDashboard(
    rows: List<CarWorkspaceShortcutRow>,
    enabled: Boolean,
    onRunSlot: (CarWorkspaceShortcutSlot) -> Unit,
    onConfigureSlot: (CarWorkspaceShortcutSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Text("Workspace dashboard", style = MaterialTheme.typography.titleLarge)
        rows.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                rowItems.forEach { row ->
                    OutlinedButton(
                        onClick = {
                            if (row.status == CarWorkspaceShortcutStatus.Configured) {
                                onRunSlot(row.slot)
                            } else {
                                onConfigureSlot(row.slot)
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = TouchTargets.PrimaryButton),
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(row.slot.displayLabel, style = MaterialTheme.typography.labelLarge)
                            Text(
                                row.statusText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            row.appCount?.let { count ->
                                Text("$count apps", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (rowItems.size == 1) {
                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
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
        title = { Text("Choose workspace for ${slot.displayLabel}") },
        text = {
            if (workspaces.isEmpty()) {
                Text("No workspaces available")
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
                                    "${workspace.appCount} apps",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (workspace.isSelected(selectedWorkspaceId)) Text("Selected")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text("Close") }
        },
        dismissButton = if (selectedWorkspaceId != null) {
            {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Clear") }
            }
        } else {
            null
        },
    )
}
