package com.trancong.dexworkspacetouch.workspace.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets

@Composable
fun WorkspaceMultiSelectToolbar(
    selectedCount: Int,
    pinnableCount: Int,
    unpinnableCount: Int,
    actionsEnabled: Boolean,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.MultiSelectToolbarMinHeight)
            .horizontalScroll(rememberScrollState())
            .semantics {
                contentDescription = "Chế độ chọn nhiều. Đã chọn $selectedCount workspace."
            },
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val title: @Composable () -> Unit = { Text("Đã chọn $selectedCount") }
        val close: @Composable (Modifier) -> Unit = { target ->
            OutlinedButton(
                onClick = onClose,
                modifier = target.height(TouchTargets.SecondaryButton),
            ) { Text("Đóng") }
        }
        val pin: @Composable (Modifier) -> Unit = { target ->
            OutlinedButton(
                onClick = onPin,
                enabled = actionsEnabled && pinnableCount > 0,
                modifier = target.height(TouchTargets.SecondaryButton).semantics {
                    contentDescription = "Ghim $pinnableCount workspace đã chọn."
                },
            ) { Text("Ghim") }
        }
        val unpin: @Composable (Modifier) -> Unit = { target ->
            OutlinedButton(
                onClick = onUnpin,
                enabled = actionsEnabled && unpinnableCount > 0,
                modifier = target.height(TouchTargets.SecondaryButton).semantics {
                    contentDescription = "Bỏ ghim $unpinnableCount workspace đã chọn."
                },
            ) { Text("Bỏ ghim") }
        }
        val delete: @Composable (Modifier) -> Unit = { target ->
            OutlinedButton(
                onClick = onDelete,
                enabled = actionsEnabled && selectedCount > 0,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = target.height(TouchTargets.SecondaryButton).semantics {
                    contentDescription = "Xóa workspace đã chọn."
                },
            ) { Text("Xóa") }
        }
        val export: @Composable (Modifier) -> Unit = { target ->
            OutlinedButton(
                onClick = onExport,
                enabled = actionsEnabled && selectedCount > 0,
                modifier = target.height(TouchTargets.SecondaryButton).semantics {
                    contentDescription = "Xuất $selectedCount workspace đã chọn."
                },
            ) { Text("Xuất") }
        }
        close(Modifier)
        title()
        pin(Modifier)
        unpin(Modifier)
        export(Modifier)
        delete(Modifier)
    }
}
