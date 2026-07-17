package com.trancong.dexworkspacetouch.workspace.templates.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.trancong.dexworkspacetouch.workspace.templates.WorkspaceTemplateCategory

@Composable
fun WorkspaceTemplatePickerDialog(
    catalog: WorkspaceTemplateCatalog,
    selectedTemplateId: String?,
    onTemplateSelected: (String, WorkspaceCanvas) -> Unit,
    onDismiss: () -> Unit,
) {
    val hostContainerWidth = LocalWindowInfo.current.containerSize.width
    val hostWidth = with(LocalDensity.current) { hostContainerWidth.toDp().value }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = shouldUsePlatformDefaultTemplateDialogWidth(
                hostWidth = hostWidth,
                largeBreakpoint = Dimensions.TemplateGridLargeBreakpoint.value,
            ),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.L),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (maxWidth > 0.dp && maxHeight > 0.dp) {
                    val size = calculateWorkspaceTemplateDialogSize(
                        availableWidth = maxWidth.value,
                        availableHeight = maxHeight.value,
                        widthFraction = Dimensions.TemplateDialogWidthFraction,
                        maximumWidth = Dimensions.TemplateDialogMaxWidth.value,
                        heightFraction = Dimensions.TemplateDialogMaxHeightFraction,
                    )
                    val columnCount = workspaceTemplateColumnCount(
                        contentWidth = size.width - Spacing.L.value * 2f,
                        mediumBreakpoint = Dimensions.TemplateGridMediumBreakpoint.value,
                        largeBreakpoint = Dimensions.TemplateGridLargeBreakpoint.value,
                    )
                    WorkspaceTemplateDialogSurface(
                        catalog = catalog,
                        selectedTemplateId = selectedTemplateId,
                        columnCount = columnCount,
                        onTemplateSelected = onTemplateSelected,
                        onDismiss = onDismiss,
                        modifier = Modifier.width(size.width.dp).height(size.height.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceTemplateDialogSurface(
    catalog: WorkspaceTemplateCatalog,
    selectedTemplateId: String?,
    columnCount: Int,
    onTemplateSelected: (String, WorkspaceCanvas) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "Chọn mẫu bố cục." },
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = DesignerElevation.DividerOverlay,
        shadowElevation = DesignerElevation.DividerOverlay,
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spacing.L,
                    top = Spacing.XS,
                    end = Spacing.L,
                    bottom = Spacing.L,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                items(WorkspaceTemplateCategory.entries, key = WorkspaceTemplateCategory::name) { category ->
                    TemplateCategorySection(
                        category = category,
                        templates = catalog.templatesIn(category),
                        selectedTemplateId = selectedTemplateId,
                        columnCount = columnCount,
                        onTemplateSelected = onTemplateSelected,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCategorySection(
    category: WorkspaceTemplateCategory,
    templates: List<WorkspaceTemplate>,
    selectedTemplateId: String?,
    columnCount: Int,
    onTemplateSelected: (String, WorkspaceCanvas) -> Unit,
) {
    var expanded by rememberSaveable(category.name) {
        mutableStateOf(category.expandedByDefault())
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimensions.TemplateSectionHeaderMinHeight)
                .semantics(mergeDescendants = true) {
                    contentDescription = category.toggleAccessibilityLabel(expanded)
                    stateDescription = if (expanded) "Đang mở" else "Đang thu gọn"
                }
                .clickable { expanded = !expanded }
                .padding(horizontal = Spacing.S),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = category.displayName(), style = MaterialTheme.typography.titleLarge)
            Text(
                text = if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        if (expanded) {
            templates.chunked(columnCount).forEach { rowTemplates ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.TemplateGrid),
                ) {
                    rowTemplates.forEach { template ->
                        Box(modifier = Modifier.weight(1f)) {
                            WorkspaceTemplateCard(
                                template = template,
                                selected = selectedTemplateId == template.id,
                                onClick = { onTemplateSelected(template.id, template.factory()) },
                            )
                        }
                    }
                    repeat(columnCount - rowTemplates.size) {
                        Box(modifier = Modifier.weight(1f))
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
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(
            if (selected) Dimensions.SelectionBorderWidth else Dimensions.CellBorderWidth,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = DesignerElevation.SnapshotCard),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTargets.SecondaryButton)
            .semantics {
                contentDescription = "Template ${template.name}, gồm ${canvas.cells.size} ô."
                this.selected = selected
            },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.S),
            verticalArrangement = Arrangement.spacedBy(Spacing.XS),
        ) {
            WorkspaceSnapshot(
                canvas = canvas,
                modifier = Modifier.fillMaxWidth(),
                showLabels = false,
                includeAccessibilitySummary = false,
            )
            Text(
                template.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.heightIn(min = Dimensions.TemplateCardTitleMinHeight),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun WorkspaceTemplateCategory.displayName(): String = when (this) {
    WorkspaceTemplateCategory.BASIC -> "Cơ bản"
    WorkspaceTemplateCategory.LEFT_RIGHT -> "Chia trái / phải"
    WorkspaceTemplateCategory.TOP_BOTTOM -> "Chia trên / dưới"
}

internal fun WorkspaceTemplateCategory.expandedByDefault(): Boolean = when (this) {
    WorkspaceTemplateCategory.BASIC,
    WorkspaceTemplateCategory.LEFT_RIGHT,
    -> true
    WorkspaceTemplateCategory.TOP_BOTTOM -> false
}

private fun WorkspaceTemplateCategory.toggleAccessibilityLabel(expanded: Boolean): String {
    val action = if (expanded) "Thu gọn" else "Mở"
    val group = when (this) {
        WorkspaceTemplateCategory.BASIC -> "Cơ bản"
        WorkspaceTemplateCategory.LEFT_RIGHT -> "Chia trái và phải"
        WorkspaceTemplateCategory.TOP_BOTTOM -> "Chia trên và dưới"
    }
    return "$action nhóm mẫu $group."
}
