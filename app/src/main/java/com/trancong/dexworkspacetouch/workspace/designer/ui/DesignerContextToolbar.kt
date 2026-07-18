package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.designer.state.DesignerContextToolbarState
import com.trancong.dexworkspacetouch.workspace.designer.state.DesignerToolbarLayoutPolicy
import com.trancong.dexworkspacetouch.workspace.designer.state.designerToolbarLayoutPolicy

@Composable
fun DesignerContextToolbar(
    state: DesignerContextToolbarState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onDecreaseDivider: () -> Unit,
    onIncreaseDivider: () -> Unit,
    onResetDivider: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Thanh công cụ thiết kế workspace." },
    ) {
        val policy = designerToolbarLayoutPolicy(
            widthDp = maxWidth.value,
            wideBreakpointDp = Dimensions.WorkspaceDesignerToolbarWideWidth.value,
        )
        if (policy == DesignerToolbarLayoutPolicy.WIDE) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistoryActions(state, onUndo, onRedo)
                ContextActions(
                    state.context,
                    onSplitHorizontal,
                    onSplitVertical,
                    onDecreaseDivider,
                    onIncreaseDivider,
                    onResetDivider,
                    onClearSelection,
                )
                ToolbarStatus(state, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                ) {
                    HistoryActions(state, onUndo, onRedo, weighted = true)
                }
                if (state.context !is DesignerContextToolbarState.Context.None) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    ) {
                        ContextActions(
                            state.context,
                            onSplitHorizontal,
                            onSplitVertical,
                            onDecreaseDivider,
                            onIncreaseDivider,
                            onResetDivider,
                            onClearSelection,
                            weighted = true,
                        )
                    }
                }
                ToolbarStatus(state)
            }
        }
    }
}

@Composable
private fun RowScope.HistoryActions(
    state: DesignerContextToolbarState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    weighted: Boolean = false,
) {
    ToolbarButton("↶ Undo", "Hoàn tác.", state.canUndo, onUndo, weighted)
    ToolbarButton("↷ Redo", "Làm lại.", state.canRedo, onRedo, weighted)
}

@Composable
private fun RowScope.ContextActions(
    context: DesignerContextToolbarState.Context,
    onSplitHorizontal: () -> Unit,
    onSplitVertical: () -> Unit,
    onDecreaseDivider: () -> Unit,
    onIncreaseDivider: () -> Unit,
    onResetDivider: () -> Unit,
    onClearSelection: () -> Unit,
    weighted: Boolean = false,
) {
    when (context) {
        DesignerContextToolbarState.Context.None -> Unit
        is DesignerContextToolbarState.Context.Cell -> {
            val disabledDescription = if (context.maximumCellsReached) {
                "Không thể chia thêm. Workspace đã có tối đa 5 ô."
            } else null
            ToolbarButton(
                "Chia ngang", disabledDescription ?: "Chia ô theo chiều ngang.",
                context.canSplitHorizontal, onSplitHorizontal, weighted,
            )
            ToolbarButton(
                "Chia dọc", disabledDescription ?: "Chia ô theo chiều dọc.",
                context.canSplitVertical, onSplitVertical, weighted,
            )
            ToolbarButton("Bỏ chọn", "Bỏ chọn ô.", true, onClearSelection, weighted)
        }
        is DesignerContextToolbarState.Context.Divider -> {
            ToolbarButton("Giảm", "Giảm tỷ lệ đường chia 5 phần trăm.", context.canDecrease, onDecreaseDivider, weighted)
            ToolbarButton("Tăng", "Tăng tỷ lệ đường chia 5 phần trăm.", context.canIncrease, onIncreaseDivider, weighted)
            ToolbarButton("50%", "Đặt lại đường chia về 50 phần trăm.", true, onResetDivider, weighted)
            ToolbarButton("Bỏ chọn", "Bỏ chọn đường chia.", true, onClearSelection, weighted)
        }
    }
}

@Composable
private fun RowScope.ToolbarButton(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    weighted: Boolean,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .then(if (weighted) Modifier.weight(1f) else Modifier)
            .heightIn(min = TouchTargets.SecondaryButton)
            .semantics { contentDescription = description },
    ) { Text(label) }
}

@Composable
private fun ToolbarStatus(state: DesignerContextToolbarState, modifier: Modifier = Modifier) {
    val text = when (val context = state.context) {
        is DesignerContextToolbarState.Context.Divider -> context.statusText
        else -> state.summary.statusText
    }
    Text(text = text, modifier = modifier.heightIn(min = Dimensions.DesignerStatusMinHeight))
}
