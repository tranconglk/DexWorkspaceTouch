package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(horizontal = Spacing.L),
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
            SearchField(state)
            FilterControls(state)
            AppPickerBody(
                state = state,
                onAppSelected = { app -> onAppSelected(cellId, app.toAssignedApp()) },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun SearchField(state: AppPickerViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S),
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
private fun FilterControls(state: AppPickerViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.S),
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        FilterButton("Tất cả", "Hiển thị tất cả ứng dụng.", AppFilter.ALL, state, Modifier.weight(1f))
        FilterButton("Người dùng", "Hiển thị ứng dụng người dùng.", AppFilter.USER, state, Modifier.weight(1f))
        FilterButton("Hệ thống", "Hiển thị ứng dụng hệ thống.", AppFilter.SYSTEM, state, Modifier.weight(1f))
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
            "Đang tải ứng dụng...",
            modifier,
            action = { CircularProgressIndicator() },
        )
        state.state.loadFailed -> StatusMessage(
            message = "Không thể đọc danh sách ứng dụng.",
            modifier = modifier,
            action = {
                Button(
                    onClick = { coroutineScope.launch { state.retry() } },
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Thử lại") }
            },
        )
        state.state.apps.isEmpty() -> StatusMessage("Không có ứng dụng có thể mở.", modifier)
        state.filteredApps.isEmpty() -> StatusMessage("Không tìm thấy ứng dụng phù hợp.", modifier)
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
    message: String,
    modifier: Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Text(message)
            action?.invoke()
        }
    }
}

@Composable
private fun InvalidAppPickerRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.L),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Không thể xác định ô cần gán ứng dụng")
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
        ) { Text("Quay lại") }
    }
}
