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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onOpenLayoutDesigner: () -> Unit, onOpenAppPicker: () -> Unit) {
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("DeX Workspace Manager", style = MaterialTheme.typography.headlineMedium)
            Text("Touch-first workspace setup")
            Button(
                onClick = onOpenLayoutDesigner,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) { Text("Create layout") }
            OutlinedButton(
                onClick = onOpenAppPicker,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
            ) { Text("Choose apps") }
        }
    }
}
