package com.trancong.dexworkspacetouch.workspace.templates.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshot
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplate
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCatalog

@Composable
fun WorkspaceTemplatePickerDialog(
    catalog: WorkspaceTemplateCatalog,
    selectedTemplateId: String?,
    onTemplateSelected: (String, WorkspaceCanvas) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(Dimensions.TemplateDialogWidthFraction)
                    .widthIn(max = Dimensions.TemplateDialogMaxWidth)
                    .fillMaxHeight(Dimensions.TemplateDialogMaxHeightFraction),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = DesignerElevation.DividerOverlay,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Dimensions.TemplateDialogHeaderHeight)
                            .padding(horizontal = Spacing.L, vertical = Spacing.S),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Chọn mẫu bố cục", style = MaterialTheme.typography.headlineSmall)
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                        ) { Text("Đóng") }
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        val columns = if (maxWidth >= Dimensions.TemplateFourColumnMinWidth) {
                            GridCells.Fixed(4)
                        } else {
                            GridCells.Adaptive(Dimensions.TemplateCardMinWidth)
                        }
                        LazyVerticalGrid(
                            columns = columns,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = Spacing.L,
                                top = Spacing.S,
                                end = Spacing.L,
                                bottom = Spacing.L,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.TemplateGrid),
                            verticalArrangement = Arrangement.spacedBy(Spacing.TemplateGrid),
                        ) {
                            items(catalog.allTemplates, key = WorkspaceTemplate::id) { template ->
                                WorkspaceTemplateCard(
                                    template = template,
                                    selected = selectedTemplateId == template.id,
                                    onClick = { onTemplateSelected(template.id, template.factory()) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTemplateCard(
    template: WorkspaceTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val canvas = remember(template.id) { template.factory() }
    Surface(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            if (selected) Dimensions.SelectionBorderWidth else Dimensions.CellBorderWidth,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.SecondaryButton)
            .semantics {
                contentDescription = "Mẫu ${template.name}"
                this.selected = selected
            }
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            WorkspaceSnapshot(
                canvas = canvas,
                modifier = Modifier.fillMaxWidth().height(Dimensions.TemplatePreviewHeight),
                showLabels = false,
                includeAccessibilitySummary = false,
            )
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            Text(
                template.description,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
