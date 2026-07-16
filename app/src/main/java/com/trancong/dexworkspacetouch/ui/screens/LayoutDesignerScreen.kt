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
                tonalElevation = 3.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) { Text("Mở") }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(64.dp),
                    ) { Text("Lưu") }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
        ) {
            TextButton(onClick = onBack) { Text("Quay lại") }
            Text("Thiết kế workspace")
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { state.undo() },
                    enabled = state.canUndo,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("↶ Undo") }
                OutlinedButton(
                    onClick = { state.redo() },
                    enabled = state.canRedo,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) { Text("↷ Redo") }
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (maxWidth > 0.dp && maxHeight > 0.dp) {
                    val fittedSize = fitSize(
                        availableWidth = maxWidth.value,
                        availableHeight = maxHeight.value,
                        aspectRatio = CANVAS_ASPECT_RATIO,
                    )
                    Box(
                        modifier = Modifier.size(fittedSize.width.dp, fittedSize.height.dp),
                    ) {
                        WorkspaceCanvasView(
                            canvas = state.canvas,
                            selectedCellId = state.selectedCellId,
                            onCellSelected = state::selectCell,
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

private const val CANVAS_ASPECT_RATIO = 16f / 10f
