package com.trancong.dexworkspacetouch.feature.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.feature.car.overlay.CarFloatingDockControlState
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader

@Composable
fun CarScreen(
    onBack: () -> Unit,
    onDismissError: () -> Unit = {},
    floatingDockState: CarFloatingDockControlState = CarFloatingDockControlState.Hidden,
    onShowFloatingDock: () -> Unit = {},
    onHideFloatingDock: () -> Unit = {},
    onAllowFloatingDock: () -> Unit = {},
    desktopShortcutSupported: Boolean = false,
    desktopShortcutStatus: String? = null,
    onAddDesktopShortcut: () -> Unit = {},
    workspaceShortcutRows: List<CarWorkspaceShortcutRow> = resolveCarWorkspaceShortcutRows(
        CarWorkspaceShortcuts.defaults(),
        emptyList(),
    ),
    visibleSlotCount: Int = CarWorkspaceShortcutCapacity.DefaultVisible,
    onVisibleSlotCountChanged: (Int) -> Unit = {},
    workspaceOptions: List<CarWorkspaceOption> = emptyList(),
    onSetWorkspaceShortcut: (CarWorkspaceShortcutSlot, String) -> Unit = { _, _ -> },
    onClearWorkspaceShortcut: (CarWorkspaceShortcutSlot) -> Unit = {},
    onWorkspaceShortcut: (CarWorkspaceShortcutSlot) -> Unit = {},
    workspaceWorkflowState: CarWorkflowExecutionState<CarWorkspaceShortcutSlot> =
        CarWorkflowExecutionState.Idle,
    workspaceActionsEnabled: Boolean = true,
    appIconLoader: AppIconLoader? = null,
) {
    var selectingShortcutSlot by rememberSaveable {
        mutableStateOf<CarWorkspaceShortcutSlot?>(null)
    }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Car Mode",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) {
                    Text("Workspaces")
                }
            }
            CarWorkspaceDashboard(
                rows = workspaceShortcutRows,
                enabled = workspaceActionsEnabled &&
                    workspaceWorkflowState !is CarWorkflowExecutionState.Running,
                onRunSlot = onWorkspaceShortcut,
                onConfigureSlot = { selectingShortcutSlot = it },
                appIconLoader = appIconLoader,
            )
            val (dockStatus, dockActionLabel) = when (floatingDockState) {
                CarFloatingDockControlState.Hidden -> "Off" to "Show"
                is CarFloatingDockControlState.Visible -> "On" to "Hide"
                CarFloatingDockControlState.PermissionRequired -> "Permission required" to "Allow"
                CarFloatingDockControlState.DisplayUnavailable -> "DeX display not available" to "Show"
                is CarFloatingDockControlState.Error -> "Unavailable" to "Show"
            }
            CarCompactControlRow(
                label = "Floating Dock",
                status = dockStatus,
                actionLabel = dockActionLabel,
                actionDescription = "$dockActionLabel Floating Dock",
                onAction = when (floatingDockState) {
                    is CarFloatingDockControlState.Visible -> onHideFloatingDock
                    CarFloatingDockControlState.PermissionRequired -> onAllowFloatingDock
                    else -> onShowFloatingDock
                },
            )
            if (floatingDockState is CarFloatingDockControlState.Error) {
                Text(
                    text = floatingDockState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            CarCompactControlRow(
                label = "Desktop shortcut",
                status = "Car Dock",
                actionLabel = "Add",
                actionDescription = "Add Car Dock desktop shortcut",
                actionEnabled = desktopShortcutSupported,
                onAction = onAddDesktopShortcut,
            )
            val shortcutStatus = desktopShortcutStatus ?: if (!desktopShortcutSupported) {
                "Launcher does not support pinned shortcuts"
            } else {
                null
            }
            shortcutStatus?.let { status ->
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
            CarVisibleShortcutCountSelector(
                visibleSlotCount = visibleSlotCount,
                onVisibleSlotCountChanged = onVisibleSlotCountChanged,
            )
            CarWorkspaceShortcutSection(
                rows = workspaceShortcutRows,
                onSlotSelected = { selectingShortcutSlot = it },
            )
            when (workspaceWorkflowState) {
                CarWorkflowExecutionState.Idle -> Unit
                is CarWorkflowExecutionState.Running -> Text(
                    text = "Opening ${workspaceWorkflowState.source.displayLabel}...",
                    style = MaterialTheme.typography.bodyLarge,
                )
                is CarWorkflowExecutionState.Error -> Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = workspaceWorkflowState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onDismissError,
                        modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }

    selectingShortcutSlot?.let { slot ->
        val selectedWorkspaceId = workspaceShortcutRows
            .firstOrNull { it.slot == slot }
            ?.workspaceId
        CarWorkspaceSelectorDialog(
            slot = slot,
            selectedWorkspaceId = selectedWorkspaceId,
            workspaces = workspaceOptions,
            onWorkspaceSelected = { workspaceId ->
                onSetWorkspaceShortcut(slot, workspaceId)
                selectingShortcutSlot = null
            },
            onClear = {
                onClearWorkspaceShortcut(slot)
                selectingShortcutSlot = null
            },
            onDismiss = { selectingShortcutSlot = null },
        )
    }
}

@Composable
private fun CarVisibleShortcutCountSelector(
    visibleSlotCount: Int,
    onVisibleSlotCountChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Visible shortcuts",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .weight(2f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End),
        ) {
            (CarWorkspaceShortcutCapacity.MinimumVisible..CarWorkspaceShortcutCapacity.Maximum)
                .forEach { count ->
                    if (count == visibleSlotCount) {
                        Button(
                            onClick = { onVisibleSlotCountChanged(count) },
                            modifier = Modifier
                                .heightIn(min = TouchTargets.PrimaryButton)
                                .semantics { contentDescription = "$count visible shortcuts, selected" },
                        ) { Text(count.toString()) }
                    } else {
                        OutlinedButton(
                            onClick = { onVisibleSlotCountChanged(count) },
                            modifier = Modifier
                                .heightIn(min = TouchTargets.PrimaryButton)
                                .semantics { contentDescription = "$count visible shortcuts" },
                        ) { Text(count.toString()) }
                    }
                }
        }
    }
}

@Composable
private fun CarCompactControlRow(
    label: String,
    status: String,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit,
    actionEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(
            onClick = onAction,
            enabled = actionEnabled,
            modifier = Modifier
                .heightIn(min = TouchTargets.SecondaryButton)
                .semantics { contentDescription = actionDescription },
        ) {
            Text(actionLabel)
        }
    }
}
