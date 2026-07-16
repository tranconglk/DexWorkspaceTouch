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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DemoApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DemoApps
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp

@Composable
fun AppPickerScreen(
    cellId: String?,
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
            onBack = onBack,
            onAppSelected = onAppSelected,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun AppPickerContent(
    cellId: String,
    onBack: () -> Unit,
    onAppSelected: (String, AssignedApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val apps = DemoApps.search(query)
    Column(modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp)) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text("Quay lại") }
        Text(
            text = "Chọn ứng dụng",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Tìm ứng dụng") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        )
        if (apps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Không tìm thấy ứng dụng phù hợp.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps, key = DemoApp::packageName) { app ->
                    DemoAppItem(
                        app = app,
                        onClick = {
                            onAppSelected(
                                cellId,
                                AssignedApp(app.packageName, app.activityName, app.label),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoAppItem(app: DemoApp, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Chọn ứng dụng ${app.label}"
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
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
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Không thể xác định ô cần gán ứng dụng")
        TextButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 56.dp),
        ) { Text("Quay lại") }
    }
}
