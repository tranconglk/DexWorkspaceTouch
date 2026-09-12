package com.trancong.dexworkspacetouch.workspace.apppicker.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import com.trancong.dexworkspacetouch.ui.design.DesignerColors
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp

@Composable
fun InstalledAppGridItem(
    app: InstalledApp,
    iconState: AppIconState,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = Dimensions.AppPickerItemMinHeight)
            .semantics {
                contentDescription = "Chọn ứng dụng ${app.label}."
                if (selected) stateDescription = "Đang được chọn."
            },
        shape = DesignerShapes.Cell,
        border = BorderStroke(
            if (selected) Dimensions.SelectionBorderWidth else Dimensions.CellBorderWidth,
            if (selected) DesignerColors.Selection else DesignerColors.CellBorder,
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                DesignerColors.SelectedCellBackground
            } else {
                DesignerColors.CellBackground
            },
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(Spacing.M),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            AppIcon(app, iconState)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selected) "Đã chọn" else "Chạm để gán",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) DesignerColors.Selection else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (selected) {
                Text(
                    text = "✓",
                    color = DesignerColors.Selection,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(app: InstalledApp, state: AppIconState) {
    Box(
        modifier = Modifier.size(Dimensions.AppPickerIconSize),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            AppIconState.Loading -> CircularProgressIndicator()
            is AppIconState.Ready -> Image(
                bitmap = state.image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
            )
            AppIconState.Fallback -> Surface(
                modifier = Modifier.fillMaxSize(),
                shape = DesignerShapes.DividerFeedback,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(app.label.take(1), style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}
