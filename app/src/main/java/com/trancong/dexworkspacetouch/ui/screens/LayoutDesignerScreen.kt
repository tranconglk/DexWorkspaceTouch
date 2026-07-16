package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.InteractionZones
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerViewModel
import com.trancong.dexworkspacetouch.workspace.designer.ui.WorkspaceCanvasView
import com.trancong.dexworkspacetouch.workspace.designer.ui.layout.fitSize

@Composable
fun LayoutDesignerScreen(
    state: WorkspaceDesignerViewModel,
    onBack: () -> Unit,
    onOpenAppPicker: (String) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
        ),
        bottomBar = {
            Surface(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
                tonalElevation = DesignerElevation.BottomBar,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(
                        horizontal = Spacing.L,
                        vertical = InteractionZones.DeadZone,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(InteractionZones.DeadZone),
                ) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(TouchTargets.PrimaryButton),
                    ) { Text("Mở") }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(TouchTargets.PrimaryButton),
                    ) { Text("Lưu") }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.L),
        ) {
            TextButton(onClick = onBack) { Text("Quay lại") }
            Text("Thiết kế workspace")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.S),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                OutlinedButton(
                    onClick = { state.undo() },
                    enabled = state.canUndo,
                    modifier = Modifier.weight(1f).heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("↶ Undo") }
                OutlinedButton(
                    onClick = { state.redo() },
                    enabled = state.canRedo,
                    modifier = Modifier.weight(1f).heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("↷ Redo") }
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = InteractionZones.DeadZone),
                contentAlignment = Alignment.Center,
            ) {
                if (maxWidth > 0.dp && maxHeight > 0.dp) {
                    val fittedSize = fitSize(
                        availableWidth = maxWidth.value,
                        availableHeight = maxHeight.value,
                        aspectRatio = Dimensions.WorkspaceAspectRatio,
                    )
                    Box(
                        modifier = Modifier.size(fittedSize.width.dp, fittedSize.height.dp),
                    ) {
                        WorkspaceCanvasView(
                            canvas = state.canvas,
                            selectedCellId = state.selectedCellId,
                            selectedDividerId = state.selectedDividerId,
                            onCellSelected = state::selectCell,
                            onDividerSelected = state::selectDivider,
                            onDividerRatioChanged = state::resizeDivider,
                            onDividerDragStart = state::beginDividerResize,
                            onDividerDragRatio = state::updateDividerResize,
                            onDividerDragEnd = { state.commitDividerResize() },
                            onDividerDragCancel = state::cancelDividerResize,
                            onClearDividerSelection = state::clearDividerSelection,
                            onChooseApp = onOpenAppPicker,
                            onSplit = { state.splitSelectedCell(it) },
                            onClearSelection = state::clearSelection,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
