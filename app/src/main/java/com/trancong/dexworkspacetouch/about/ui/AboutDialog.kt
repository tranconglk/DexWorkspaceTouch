package com.trancong.dexworkspacetouch.about.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trancong.dexworkspacetouch.about.presentation.AppDiagnosticInfo
import com.trancong.dexworkspacetouch.about.presentation.displayLabel
import com.trancong.dexworkspacetouch.about.presentation.formatDevice
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.DesignerElevation
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets

@Composable
fun AboutDialog(
    info: AppDiagnosticInfo,
    copySuccessVisible: Boolean,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(Spacing.L),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = Dimensions.AboutDialogMaxWidth)
                        .heightIn(max = maxHeight)
                        .semantics {
                            contentDescription = "Thông tin ứng dụng DexWorkspaceTouch."
                        },
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = DesignerElevation.AboutDialog,
                ) {
                    Column(
                        modifier = Modifier.padding(Spacing.L),
                        verticalArrangement = Arrangement.spacedBy(Spacing.M),
                    ) {
                        Text("DexWorkspaceTouch", style = MaterialTheme.typography.headlineSmall)
                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Dimensions.AboutSectionSpacing),
                        ) {
                            AboutSection(
                                rows = listOf(
                                    "Phiên bản" to "${info.versionName} (${info.versionCode})",
                                    "Trạng thái" to "Beta",
                                    "Build type" to info.buildChannel,
                                    "Build commit" to info.buildCommit.ifBlank { "unknown" },
                                    "Build date" to info.buildDateUtc.ifBlank { "unknown" },
                                ),
                            )
                            AboutSection(
                                rows = listOf(
                                    "Database" to "v${info.databaseVersion}",
                                    "Workspace transfer" to ".dwt v${info.workspaceTransferVersion}",
                                    "Library backup" to ".dwtbundle v${info.libraryBundleVersion}",
                                ),
                            )
                            AboutSection(
                                rows = listOf(
                                    "Android SDK thiết bị" to info.androidSdk.toString(),
                                    "Thiết bị" to formatDevice(info.manufacturer, info.model),
                                    "Hiển thị" to displayLabel(info.appDisplayMode),
                                    "Display ID" to (info.currentDisplayId?.toString() ?: "unknown"),
                                ),
                            )
                        }
                        if (copySuccessVisible) {
                            Text(
                                "Đã sao chép thông tin chẩn đoán.",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        AboutActions(onCopy = onCopy, onDismiss = onDismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        rows.forEach { (label, value) -> AboutInfoRow(label, value) }
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimensions.AboutInfoRowMinHeight),
    ) {
        if (maxWidth >= Dimensions.AboutWideLayoutBreakpoint) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.M),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text(value, modifier = Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(value)
            }
        }
    }
}

@Composable
private fun AboutActions(onCopy: () -> Unit, onDismiss: () -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val wide = maxWidth >= Dimensions.AboutWideLayoutBreakpoint
        if (wide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Đóng") }
                Button(
                    onClick = onCopy,
                    modifier = Modifier
                        .heightIn(min = TouchTargets.SecondaryButton)
                        .semantics { contentDescription = "Sao chép thông tin chẩn đoán." },
                ) { Text("Sao chép thông tin") }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Button(
                    onClick = onCopy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTargets.SecondaryButton)
                        .semantics { contentDescription = "Sao chép thông tin chẩn đoán." },
                ) { Text("Sao chép thông tin") }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.SecondaryButton),
                ) { Text("Đóng") }
            }
        }
    }
}
