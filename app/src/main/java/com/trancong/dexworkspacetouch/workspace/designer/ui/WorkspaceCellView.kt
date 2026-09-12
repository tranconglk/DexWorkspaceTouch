package com.trancong.dexworkspacetouch.workspace.designer.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerAnimation
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.InteractionZones
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toStableKey
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WorkspaceCellView(
    cell: WorkspaceCell,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    appIconLoader: AppIconLoader? = null,
) {
    val description = cell.app?.let { "Ứng dụng ${it.label}. Chạm để đổi ứng dụng." }
        ?: "Ô trống. Chạm để chọn ứng dụng."
    val borderColor by animateColorAsState(
        targetValue = if (selected) DesignerColors.Selection else DesignerColors.CellBorder,
        animationSpec = tween(DesignerAnimation.FastDurationMillis),
        label = "selectedCellBorder",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) DesignerColors.SelectedCellBackground else DesignerColors.CellBackground,
        animationSpec = tween(DesignerAnimation.FastDurationMillis),
        label = "selectedCellContainer",
    )
    val border = if (selected) {
        BorderStroke(Dimensions.SelectionBorderWidth, borderColor)
    } else {
        BorderStroke(Dimensions.CellBorderWidth, borderColor)
    }

    Card(
        modifier = modifier
            .clip(DesignerShapes.Cell)
            .semantics(mergeDescendants = false) {
                contentDescription = description
                if (selected) stateDescription = "Đang được chọn"
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = DesignerShapes.Cell,
        border = border,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = InteractionZones.DeadZone,
                        top = InteractionZones.DeadZone,
                        end = InteractionZones.DeadZone,
                        bottom = if (selected) {
                            Dimensions.SelectedCellContentBottomPadding
                        } else {
                            InteractionZones.DeadZone
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.CenterVertically),
            ) {
                if (cell.app == null) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.headlineLarge,
                        color = DesignerColors.Accent,
                    )
                    Text(
                        text = "Chạm để chọn ứng dụng",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    DesignerAppIcon(cell.app, appIconLoader)
                    Text(
                        text = cell.app.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignerAppIcon(app: AssignedApp, appIconLoader: AppIconLoader?) {
    val identity = app.toIdentity()
    val iconState by produceState<AppIconState>(AppIconState.Loading, identity.toStableKey(), appIconLoader) {
        value = if (appIconLoader == null) {
            AppIconState.Fallback
        } else {
            withContext(Dispatchers.IO) { appIconLoader.loadIcon(identity) }
        }
    }
    Surface(
        modifier = Modifier.size(Dimensions.AppPickerIconSize),
        shape = DesignerShapes.DividerFeedback,
        color = DesignerColors.SelectedCellBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            val ready = iconState as? AppIconState.Ready
            if (ready != null) {
                Image(
                    bitmap = ready.image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = app.label.firstOrNull()?.uppercase() ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = DesignerColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun EmptyWorkspaceCellPreview() {
    MaterialTheme {
        WorkspaceCellView(
            cell = WorkspaceCell("empty", NormalizedBounds.FullCanvas),
            selected = false,
            onClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun AssignedWorkspaceCellPreview() {
    MaterialTheme {
        WorkspaceCellView(
            cell = WorkspaceCell(
                id = "notes",
                bounds = NormalizedBounds.FullCanvas,
                app = AssignedApp("com.example.notes", "com.example.notes.MainActivity", "Ghi chú"),
            ),
            selected = true,
            onClick = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
