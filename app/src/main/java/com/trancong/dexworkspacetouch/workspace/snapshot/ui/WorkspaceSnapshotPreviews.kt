package com.trancong.dexworkspacetouch.workspace.snapshot.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme

@Preview(showBackground = true, widthDp = 480)
@Composable
private fun EmptySnapshotPreview() = SnapshotPreview(WorkspaceSnapshotDemoData.empty())

@Preview(showBackground = true, widthDp = 480)
@Composable
private fun TwoCellSnapshotPreview() = SnapshotPreview(WorkspaceSnapshotDemoData.two())

@Preview(showBackground = true, widthDp = 480)
@Composable
private fun ThreeCellSnapshotPreview() = SnapshotPreview(WorkspaceSnapshotDemoData.three())

@Preview(showBackground = true, widthDp = 480)
@Composable
private fun FourCellSnapshotPreview() = SnapshotPreview(WorkspaceSnapshotDemoData.four())

@Preview(showBackground = true, widthDp = 240)
@Composable
private fun SmallCardSnapshotPreview() {
    DexWorkspaceTouchTheme(darkTheme = false) {
        Card(
            modifier = Modifier.padding(Spacing.M),
            shape = DesignerShapes.Workspace,
            elevation = CardDefaults.cardElevation(defaultElevation = DesignerElevation.SnapshotCard),
        ) {
            WorkspaceSnapshot(
                canvas = WorkspaceSnapshotDemoData.four(),
                showLabels = false,
                modifier = Modifier.fillMaxWidth().padding(Spacing.S),
            )
        }
    }
}

@Composable
private fun SnapshotPreview(canvas: com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas, showLabels: Boolean = true) {
    DexWorkspaceTouchTheme(darkTheme = false) {
        WorkspaceSnapshot(
            canvas = canvas,
            showLabels = showLabels,
            modifier = Modifier.fillMaxWidth().padding(Spacing.M),
        )
    }
}
