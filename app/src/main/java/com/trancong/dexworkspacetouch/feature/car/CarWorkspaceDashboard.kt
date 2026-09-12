package com.trancong.dexworkspacetouch.feature.car

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.DesignerAnimation
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconLoader
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconState
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity

@Composable
fun CarWorkspaceDashboard(
    rows: List<CarWorkspaceShortcutRow>,
    enabled: Boolean,
    onRunSlot: (CarWorkspaceShortcutSlot) -> Unit,
    onConfigureSlot: (CarWorkspaceShortcutSlot) -> Unit,
    appIconLoader: AppIconLoader? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text("Bảng điều khiển Workspace", style = MaterialTheme.typography.headlineSmall)
        rows.chunked(DashboardColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                rowItems.forEach { row ->
                    CarWorkspaceDashboardCard(
                        row = row,
                        enabled = enabled,
                        appIconLoader = appIconLoader,
                        onClick = {
                            if (row.status == CarWorkspaceShortcutStatus.Configured) {
                                onRunSlot(row.slot)
                            } else {
                                onConfigureSlot(row.slot)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    }
}

@Composable
private fun CarWorkspaceDashboardCard(
    row: CarWorkspaceShortcutRow,
    enabled: Boolean,
    appIconLoader: AppIconLoader?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warning = row.status == CarWorkspaceShortcutStatus.Unavailable
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val normalColor = when {
        warning -> MaterialTheme.colorScheme.errorContainer
        row.status == CarWorkspaceShortcutStatus.Unconfigured -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val containerColor by animateColorAsState(
        targetValue = if (pressed) MaterialTheme.colorScheme.primaryContainer else normalColor,
        animationSpec = tween(DesignerAnimation.FastDurationMillis),
        label = "carWorkspaceCardPress",
    )
    Card(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            if (pressed) 2.dp else 1.dp,
            if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            disabledContainerColor = normalColor.copy(alpha = 0.62f),
        ),
        modifier = modifier
            .heightIn(min = DashboardCardMinimumHeight)
            .semantics { contentDescription = row.dashboardAccessibilityLabel },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                    .padding(Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            CarWorkspacePreviewSurface(
                row = row,
                appIconLoader = appIconLoader,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DashboardPreviewHeight),
            )
            Text(
                text = row.dashboardTitle,
                style = MaterialTheme.typography.titleSmall,
                color = if (warning) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CarWorkspacePreviewSurface(
    row: CarWorkspaceShortcutRow,
    appIconLoader: AppIconLoader?,
    modifier: Modifier = Modifier,
) {
    val previewShape = MaterialTheme.shapes.small
    val icons = remember(row.preview, appIconLoader) {
        row.preview?.cells.orEmpty().associateWith { cell ->
            cell.appIdentity?.let { identity -> appIconLoader?.loadIcon(identity) }
        }
    }
    Box(
        modifier = modifier
            .clip(previewShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, previewShape),
        contentAlignment = Alignment.Center,
    ) {
        when (row.status) {
            CarWorkspaceShortcutStatus.Configured -> BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(PreviewPadding),
            ) {
                row.preview?.cells.orEmpty().forEach { cell ->
                    val bounds = cell.bounds
                    val cellWidth = maxWidth * (bounds.right - bounds.left)
                    val cellHeight = maxHeight * (bounds.bottom - bounds.top)
                    Box(
                        modifier = Modifier
                            .offset(maxWidth * bounds.left, maxHeight * bounds.top)
                            .size(cellWidth, cellHeight)
                            .padding(PreviewCellGap)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val identity = cell.appIdentity
                        val icon = icons[cell]
                        if (icon is AppIconState.Ready) {
                            Image(
                                bitmap = icon.image,
                                contentDescription = null,
                                modifier = Modifier.size(PreviewIconSize),
                            )
                        } else if (identity != null) {
                            Text(
                                text = carWorkspaceFallbackIconLabel(identity),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
            CarWorkspaceShortcutStatus.Unconfigured -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                Text("+", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("Chưa cấu hình", style = MaterialTheme.typography.labelLarge)
            }
            CarWorkspaceShortcutStatus.Unavailable -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.XS),
            ) {
                Text("!", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.error)
                Text("Không khả dụng", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private const val DashboardColumns = 2
private val DashboardCardMinimumHeight = TouchTargets.SecondaryButton + 72.dp
private val DashboardPreviewHeight = Dimensions.CarDashboardPreviewHeight
private val PreviewPadding = 5.dp
private val PreviewCellGap = 2.dp
private val PreviewIconSize = 34.dp

internal fun carWorkspaceFallbackIconLabel(identity: AppIdentity): String =
    identity.packageName.firstOrNull()?.uppercase() ?: "?"
