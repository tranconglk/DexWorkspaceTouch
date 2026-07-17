package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toAssignedApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toStableKey
import com.trancong.dexworkspacetouch.workspace.apppicker.presentation.AppPickerViewModel
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp

@Composable
fun AppPickerScreen(
    cellId: String?,
    state: AppPickerViewModel,
    onBack: () -> Unit,
    onAppSelected: (String, AssignedApp) -> Unit,
) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        if (cellId == null) {
            InvalidAppPickerRoute(
                onBack = onBack,
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        AppPickerContent(
            cellId = cellId,
            state = state,
            onBack = onBack,
            onAppSelected = onAppSelected,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun AppPickerContent(
    cellId: String,
    state: AppPickerViewModel,
    onBack: () -> Unit,
    onAppSelected: (String, AssignedApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val apps = state.filteredApps
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = Spacing.L)) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
        ) { Text("Quay lại") }
        Text(
            text = "Chọn ứng dụng",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = state::updateQuery,
            label = { Text("Tìm ứng dụng") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.M),
        )
        if (state.state.loadFailed) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không thể đọc danh sách ứng dụng.")
            }
        } else if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không tìm thấy ứng dụng phù hợp.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                items(apps, key = { app -> app.identity.toStableKey() }) { app ->
                    InstalledAppItem(
                        app = app,
                        onClick = {
                            onAppSelected(
                                cellId,
                                app.toAssignedApp(),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledAppItem(app: InstalledApp, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.AppRowMinHeight)
            .semantics(mergeDescendants = true) {
                contentDescription = "Chọn ứng dụng ${app.label}"
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Surface(
                modifier = Modifier.size(Dimensions.AppIconSize),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(app.label.take(1), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(app.label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun InvalidAppPickerRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
