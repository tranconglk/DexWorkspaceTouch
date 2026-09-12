package com.trancong.dexworkspacetouch.feature.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.ui.design.Dimensions
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
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
        Column(
            modifier = Modifier
                .widthIn(max = Dimensions.CarContentMaxWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Car Mode",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Mở nhanh workspace trên Samsung DeX",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = TouchTargets.PrimaryButton),
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
                modifier = Modifier
                    .widthIn(max = Dimensions.CarDashboardMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )
            val (dockStatus, dockActionLabel) = when (floatingDockState) {
                CarFloatingDockControlState.Hidden -> "Off" to "Show"
                is CarFloatingDockControlState.Visible -> "On" to "Hide"
                CarFloatingDockControlState.PermissionRequired -> "Permission required" to "Allow"
                CarFloatingDockControlState.DisplayUnavailable -> "DeX display not available" to "Show"
                is CarFloatingDockControlState.Error -> "Unavailable" to "Show"
            }
            CarConfigurationSection(
                title = "Quick access",
                supportingText = "Điều khiển lối tắt luôn sẵn sàng trên DeX.",
                modifier = Modifier
                    .widthIn(max = Dimensions.CarConfigurationMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            ) {
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
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CarConfigurationSection(
                title = "Dashboard configuration",
                supportingText = "Chọn số lượng và workspace cho từng vị trí.",
                modifier = Modifier
                    .widthIn(max = Dimensions.CarConfigurationMaxWidth)
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            ) {
                CarVisibleShortcutCountSelector(
                    visibleSlotCount = visibleSlotCount,
                    onVisibleSlotCountChanged = onVisibleSlotCountChanged,
                )
                CarWorkspaceShortcutSection(
                    rows = workspaceShortcutRows,
                    onSlotSelected = { selectingShortcutSlot = it },
                )
            }
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
private fun CarConfigurationSection(
    title: String,
    supportingText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
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
                                .semantics {
                                    contentDescription = "$count visible shortcuts, selected"
                                    stateDescription = "Selected"
                                },
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
        modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.PrimaryButton),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
