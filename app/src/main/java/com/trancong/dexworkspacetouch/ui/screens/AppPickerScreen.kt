package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.DwtStatusSurface
import com.trancong.dexworkspacetouch.ui.design.DwtStatusTone
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toAssignedApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toStableKey
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppFilter
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppIconState
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppPickerViewModel
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.InstalledAppGridItem
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import kotlinx.coroutines.launch

@Composable
fun AppPickerScreen(
    cellId: String?,
    state: AppPickerViewModel,
    onBack: () -> Unit,
    onAppSelected: (String, AssignedApp) -> Unit,
) {
    LaunchedEffect(state) { state.loadApps() }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        if (cellId == null) {
            InvalidAppPickerRoute(onBack, Modifier.padding(innerPadding))
            return@Scaffold
        }
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding).imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = Dimensions.GridContentMaxWidth)
                    .padding(horizontal = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Quay lại") }
                Text(
                    text = "Chọn ứng dụng",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
                AppPickerControls(state)
                AppPickerBody(
                    state = state,
                    onAppSelected = { app -> onAppSelected(cellId, app.toAssignedApp()) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AppPickerControls(state: AppPickerViewModel) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = DesignerElevation.SnapshotCard,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(Spacing.M)) {
            if (maxWidth >= Dimensions.AppPickerControlsWideWidth) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchField(state, Modifier.weight(1f))
                    FilterControls(state, compact = true)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    SearchField(state, Modifier.fillMaxWidth())
                    FilterControls(state, compact = false)
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: AppPickerViewModel, modifier: Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = state::updateQuery,
            label = { Text("Tìm ứng dụng") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (state.query.isNotEmpty()) {
            TextButton(
                onClick = { state.updateQuery("") },
                modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
            ) { Text("Xóa") }
        }
    }
}

@Composable
private fun FilterControls(state: AppPickerViewModel, compact: Boolean) {
    Row(
        modifier = if (compact) Modifier else Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        val itemModifier: Modifier = if (compact) Modifier else Modifier.weight(1f)
        FilterButton("Tất cả", "Hiển thị tất cả ứng dụng.", AppFilter.ALL, state, itemModifier)
        FilterButton("Người dùng", "Hiển thị ứng dụng người dùng.", AppFilter.USER, state, itemModifier)
        FilterButton("Hệ thống", "Hiển thị ứng dụng hệ thống.", AppFilter.SYSTEM, state, itemModifier)
    }
}

@Composable
private fun FilterButton(
    label: String,
    description: String,
    filter: AppFilter,
    state: AppPickerViewModel,
    modifier: Modifier,
) {
    val buttonModifier = modifier
        .heightIn(min = TouchTargets.AppFilterMinHeight)
        .semantics { contentDescription = description }
    if (state.filter == filter) {
        Button(onClick = { state.updateFilter(filter) }, modifier = buttonModifier) { Text(label) }
    } else {
        OutlinedButton(onClick = { state.updateFilter(filter) }, modifier = buttonModifier) { Text(label) }
    }
}

@Composable
private fun AppPickerBody(
    state: AppPickerViewModel,
    onAppSelected: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    when {
        state.state.isLoading -> StatusMessage(
            title = "Đang tải ứng dụng",
            modifier = modifier,
            action = { CircularProgressIndicator() },
        )
        state.state.loadFailed -> StatusMessage(
            title = "Không thể đọc danh sách ứng dụng",
            modifier = modifier,
            tone = DwtStatusTone.Error,
            action = {
                Button(
                    onClick = { coroutineScope.launch { state.retry() } },
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Thử lại") }
            },
        )
        state.state.apps.isEmpty() -> StatusMessage("Không có ứng dụng có thể mở", modifier)
        state.filteredApps.isEmpty() -> StatusMessage(
            "Không tìm thấy ứng dụng phù hợp",
            modifier,
            supportingText = "Thử đổi từ khóa hoặc bộ lọc.",
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(Dimensions.AppPickerItemMinWidth),
            modifier = modifier,
            contentPadding = PaddingValues(vertical = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(Spacing.AppPickerGrid),
            verticalArrangement = Arrangement.spacedBy(Spacing.AppPickerGrid),
        ) {
            items(state.filteredApps, key = { app -> app.identity.toStableKey() }) { app ->
                InstalledAppItemWithIcon(app, state, onAppSelected)
            }
        }
    }
}

@Composable
private fun InstalledAppItemWithIcon(
    app: InstalledApp,
    state: AppPickerViewModel,
    onAppSelected: (InstalledApp) -> Unit,
) {
    val iconState by produceState<AppIconState>(AppIconState.Loading, app.identity) {
        value = state.loadIcon(app.identity)
    }
    InstalledAppGridItem(
        app = app,
        iconState = iconState,
        selected = state.isSelected(app),
        onClick = { onAppSelected(app) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusMessage(
    title: String,
    modifier: Modifier,
    supportingText: String? = null,
    tone: DwtStatusTone = DwtStatusTone.Info,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        DwtStatusSurface(
            tone = tone,
            title = title,
            supportingText = supportingText,
            modifier = Modifier.widthIn(max = Dimensions.UpdateContentMaxWidth),
            trailingContent = action,
        )
    }
}

@Composable
private fun InvalidAppPickerRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
        DwtStatusSurface(
            tone = DwtStatusTone.Error,
            title = "Không thể xác định ô cần gán ứng dụng",
            modifier = Modifier.widthIn(max = Dimensions.UpdateContentMaxWidth),
            trailingContent = {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Quay lại") }
            },
        )
    }
}
