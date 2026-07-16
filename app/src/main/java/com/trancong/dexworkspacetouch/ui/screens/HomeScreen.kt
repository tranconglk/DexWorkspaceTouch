package com.trancong.dexworkspacetouch.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshot
import com.trancong.dexworkspacetouch.workspace.snapshot.ui.WorkspaceSnapshotDemoData

@Composable
fun HomeScreen(onOpenLayoutDesigner: () -> Unit, onOpenAppPicker: () -> Unit) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            Text("DeX Workspace Manager", style = MaterialTheme.typography.headlineMedium)
            Text("Touch-first workspace setup")
            Card(
                shape = DesignerShapes.Workspace,
                elevation = CardDefaults.cardElevation(defaultElevation = DesignerElevation.SnapshotCard),
            ) {
                WorkspaceSnapshot(
                    canvas = WorkspaceSnapshotDemoData.three(),
                    modifier = Modifier.fillMaxWidth().padding(Spacing.M),
                )
            }
            Button(
                onClick = onOpenLayoutDesigner,
                modifier = Modifier.fillMaxWidth().height(TouchTargets.PrimaryButton),
                contentPadding = PaddingValues(horizontal = Spacing.L),
            ) { Text("Create layout") }
            OutlinedButton(
                onClick = onOpenAppPicker,
                modifier = Modifier.fillMaxWidth().height(TouchTargets.SecondaryButton),
                contentPadding = PaddingValues(horizontal = Spacing.L),
            ) { Text("Choose apps") }
        }
    }
}
