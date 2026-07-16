package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.workspace.designer.model.SplitDirection
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.designer.state.SplitResult
import com.trancong.dexworkspacetouch.workspace.designer.state.WorkspaceDesignerStateHolder
import com.trancong.dexworkspacetouch.workspace.designer.ui.WorkspaceCanvasView

@Composable
fun LayoutDesignerScreen(
    state: WorkspaceDesignerStateHolder,
    onBack: () -> Unit,
    onOpenAppPicker: (String) -> Unit,
) {
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Mở") }
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text("Lưu") }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
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
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp),
            ) {
                WorkspaceCanvasView(
                    canvas = state.canvas,
                    selectedCellId = state.selectedCellId,
                    onCellSelected = state::selectCell,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val selectedCell = state.selectedCellId?.let { selectedId ->
                state.canvas.cells.firstOrNull { it.id == selectedId }
            }
            selectedCell?.let { cell ->
                CellActionPanel(
                    cell = cell,
                    cellCount = state.canvas.cells.size,
                    onChooseApp = { onOpenAppPicker(cell.id) },
                    onSplit = state::splitSelectedCell,
                    onClearSelection = state::clearSelection,
                )
            }
        }
    }
}

@Composable
private fun CellActionPanel(
    cell: WorkspaceCell,
    cellCount: Int,
    onChooseApp: () -> Unit,
    onSplit: (SplitDirection) -> SplitResult,
    onClearSelection: () -> Unit,
) {
    val maximumReached = cellCount >= 4
    val canSplitHorizontal = !maximumReached && cell.bounds.height / 2f >= 0.2f
    val canSplitVertical = !maximumReached && cell.bounds.width / 2f >= 0.2f

    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Đã chọn ô: ${cell.id}")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onChooseApp,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) {
                Text(if (cell.app == null) "Chọn ứng dụng" else "Đổi ứng dụng")
            }
            OutlinedButton(
                onClick = onClearSelection,
                modifier = Modifier.weight(1f).heightIn(min = 64.dp),
            ) { Text("Bỏ chọn") }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { onSplit(SplitDirection.HORIZONTAL) },
                enabled = canSplitHorizontal,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .semantics {
                        contentDescription = "Chia ô thành phần trên và phần dưới"
                    },
            ) { Text("Chia ngang") }
            OutlinedButton(
                onClick = { onSplit(SplitDirection.VERTICAL) },
                enabled = canSplitVertical,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .semantics {
                        contentDescription = "Chia ô thành phần trái và phần phải"
                    },
            ) { Text("Chia dọc") }
        }
        when {
            maximumReached -> Text("Tối đa 4 ô trong phiên bản này.")
            !canSplitHorizontal || !canSplitVertical -> Text("Ô này quá nhỏ để chia tiếp.")
        }
    }
}
