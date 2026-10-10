package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets
import kotlinx.coroutines.delay

@Composable
fun PhysicalButtonSettingsScreen(activity: Activity, onBack: () -> Unit) {
    val app = activity.application as DexWorkspaceTouchApplication
    val preferences = app.physicalButtonPreferences
    val configuration by preferences.configuration.collectAsState()
    val bindingRequested by preferences.bindingRequested.collectAsState()
    val workspaceFlow = remember(app) { app.workspaceRepository.observeAll() }
    val workspaces by workspaceFlow.collectAsState(initial = emptyList())
    val accessibilityManager = remember(activity) { activity.getSystemService(AccessibilityManager::class.java) }
    val serviceComponent = remember(activity) { ComponentName(activity, PhysicalButtonAccessibilityService::class.java) }
    fun serviceEnabled(): Boolean = accessibilityManager
        .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == serviceComponent }
    var accessibilityEnabled by remember { mutableStateOf(serviceEnabled()) }
    var workspaceMenuOpen by remember { mutableStateOf(false) }

    DisposableEffect(preferences) {
        onDispose { preferences.cancelBinding() }
    }
    LaunchedEffect(bindingRequested) {
        if (bindingRequested) {
            delay(30_000L)
            preferences.cancelBinding()
        }
    }

    DisposableEffect(activity) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { accessibilityEnabled = serviceEnabled() }
        }
        val resolver = activity.contentResolver
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer)
        resolver.registerContentObserver(Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) {
                Text("Quay lại")
            }
            Text("Physical button (Thử nghiệm)", style = MaterialTheme.typography.headlineSmall)
            Text("Liên kết nút Bluetooth/USB, rồi bấm Volume Up trên nút đã liên kết để mở Workspace trên DeX, kể cả khi DWT ở nền.")
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (configuration.enabled) "Physical button: Đã bật" else "Physical button: Đã tắt")
                Switch(checked = configuration.enabled, onCheckedChange = preferences::setEnabled)
            }
            Column {
                val selected = workspaces.firstOrNull { it.id == configuration.workspaceId }
                OutlinedButton(onClick = { workspaceMenuOpen = true }, modifier = Modifier.fillMaxWidth().heightIn(min = TouchTargets.SecondaryButton)) {
                    Text(selected?.name ?: if (configuration.workspaceId == null) "Chọn Workspace ▼" else "Workspace đã xóa hoặc không khả dụng ▼")
                }
                DropdownMenu(expanded = workspaceMenuOpen, onDismissRequest = { workspaceMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Không chọn") }, onClick = {
                        preferences.setWorkspace(null)
                        workspaceMenuOpen = false
                    })
                    workspaces.forEach { workspace ->
                        DropdownMenuItem(text = { Text(workspace.name) }, onClick = {
                            preferences.setWorkspace(workspace.id)
                            workspaceMenuOpen = false
                        })
                    }
                }
            }
            if (configuration.workspaceId == null) Text("Chọn Workspace trước khi dùng nút.")
            else if (workspaces.none { it.id == configuration.workspaceId }) Text("Workspace đã chọn không khả dụng. DWT sẽ không mở Workspace khác.")
            Text(if (accessibilityEnabled) "Accessibility service: Đã bật" else "Accessibility service: Đã tắt")
            Button(onClick = {
                runCatching { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .onFailure { Toast.makeText(activity, "Không thể mở cài đặt Trợ năng.", Toast.LENGTH_SHORT).show() }
            }, modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) {
                Text("Mở cài đặt Trợ năng")
            }
            Text("Bật dịch vụ DWT Physical Button trong Trợ năng để nhận phím. Nút Volume Up trên điện thoại vẫn hoạt động bình thường.")
            Text("Remote")
            Text(if (configuration.deviceDescriptor.isNullOrBlank()) "Chưa liên kết"
                else configuration.deviceName ?: "Đã liên kết")
            if (bindingRequested) {
                Text("Đang chờ trong 30 giây: bấm nút Volume Up trên remote một lần. Lần bấm này không đổi âm lượng hoặc mở Workspace.")
                OutlinedButton(onClick = preferences::cancelBinding,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Hủy chờ") }
            } else {
                Button(onClick = preferences::beginBinding, enabled = accessibilityEnabled,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) {
                    Text(if (configuration.deviceDescriptor.isNullOrBlank()) "Liên kết remote" else "Liên kết remote khác")
                }
            }
            if (!configuration.deviceDescriptor.isNullOrBlank()) {
                OutlinedButton(onClick = preferences::clearRemoteBinding,
                    modifier = Modifier.heightIn(min = TouchTargets.SecondaryButton)) { Text("Xóa liên kết") }
            }
            Text("Chỉ nhận Volume Up từ remote đã liên kết. Khi chưa liên kết hoặc dùng thiết bị khác, Android xử lý phím bình thường. Chỉ mở Workspace khi có đúng một màn hình ngoài đang hoạt động.")
        }
    }
}
