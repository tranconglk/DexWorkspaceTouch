package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.accessibilityservice.AccessibilityService
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.license.runtime.LicenseGateUiState
import com.trancong.dexworkspacetouch.license.runtime.LicenseRuntimeCoordinator
import com.trancong.dexworkspacetouch.platform.launch.android.DisplayTargetWorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchUiState
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchViewModel
import com.trancong.dexworkspacetouch.workspace.library.model.toLibraryItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Chỉ lọc hardware key; không đọc cây UI, click UI hoặc mô phỏng touch. */
class PhysicalButtonAccessibilityService : AccessibilityService() {
    private val keyHandler = PhysicalButtonKeyHandler()
    // Post dispatch sau khi onKeyEvent trả về để không giữ callback lọc phím.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val viewModelStore = ViewModelStore()
    private val hostToken = Any()
    private lateinit var launchViewModel: WorkspaceLaunchViewModel
    private lateinit var dispatcher: PhysicalButtonActionDispatcher
    private lateinit var preferences: PhysicalButtonPreferences
    private var dispatchJob: Job? = null
    private var targetDisplayId: Int? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as DexWorkspaceTouchApplication
        preferences = app.physicalButtonPreferences
        val displayManager = getSystemService(DisplayManager::class.java)
        val requestFactory = WorkspaceLaunchRequestFactory(
            DefaultInstalledAppCatalog(AndroidInstalledAppDataSource.create(applicationContext)),
        )
        launchViewModel = ViewModelProvider(
            viewModelStore, WorkspaceLaunchViewModel.factory(requestFactory, executionArbiter = app.carWorkflowExecutionArbiter),
        )[WorkspaceLaunchViewModel::class.java]
        val licenseCoordinator = ViewModelProvider(
            viewModelStore, LicenseRuntimeCoordinator.factory(app.licenseRepository, app.licenseTimeProvider),
        )[LicenseRuntimeCoordinator::class.java]
        val runtime = DisplayTargetWorkspaceLaunchRuntime(
            context = this,
            diagnostics = app.workspaceLaunchDiagnostics,
            displayProvider = { targetDisplayId?.let(displayManager::getDisplay) },
            repairController = app.workspaceRepairController,
        )
        dispatcher = PhysicalButtonActionDispatcher(
            preferences = preferences,
            repository = app.workspaceRepository,
            canLaunch = {
                // Dùng nguyên gate hiện có, kể cả sau khi service sống lâu ở background.
                licenseCoordinator.bootstrap()
                licenseCoordinator.state.first { it !is LicenseGateUiState.Checking } is LicenseGateUiState.Allowed
            },
            launchWorkspace = { workspace ->
                // Không tự chọn giữa nhiều màn hình ngoài hoặc chuyển sang màn hình điện thoại.
                val display = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
                    .filter {
                        it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON &&
                            it.flags and Display.FLAG_PRIVATE == 0
                    }.singleOrNull()
                if (display == null || isLaunching() || app.carWorkflowExecutionArbiter.isRunning.value) false
                else app.embeddedProductRunGate.tryDispatchClassic {
                    targetDisplayId = display.displayId
                    launchViewModel.launchWorkspace(workspace.toLibraryItem(), runtime, hostToken)
                }
            },
        )
        serviceScope.launch {
            launchViewModelSnapshotLog()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        val configuration = preferences.configuration.value
        val decision = keyHandler.handle(
            event = PhysicalButtonKeyEvent(
                keyCode = event.keyCode,
                action = event.action,
                repeatCount = event.repeatCount,
                deviceId = event.deviceId,
                downTime = event.downTime,
                isExternal = device?.isExternal == true && !device.isVirtual,
                deviceDescriptor = device?.descriptor,
            ),
            enabled = configuration.enabled && !configuration.workspaceId.isNullOrBlank(),
        )
        if (decision == PhysicalButtonKeyDecision.PASS_THROUGH) return false
        if (decision == PhysicalButtonKeyDecision.TRIGGER_WORKSPACE) {
            Log.i(LOG_TAG, "Trigger device=${device?.name} deviceId=${event.deviceId}")
            if (dispatchJob?.isActive != true && !isLaunching()) {
                dispatchJob = serviceScope.launch {
                    Log.i(LOG_TAG, "Workspace action=${dispatcher.dispatch()}")
                }
            }
        }
        return true
    }

    private fun isLaunching(): Boolean = launchViewModel.state is WorkspaceLaunchUiState.Checking ||
        launchViewModel.state is WorkspaceLaunchUiState.Launching

    private suspend fun launchViewModelSnapshotLog() {
        androidx.compose.runtime.snapshotFlow { launchViewModel.state }.collect { state ->
            when (state) {
                is WorkspaceLaunchUiState.Completed -> Log.i(LOG_TAG,
                    "Workspace completed id=${state.workspaceId} result=${state.result.javaClass.simpleName}")
                is WorkspaceLaunchUiState.LaunchError -> Log.w(LOG_TAG, "Workspace launch unavailable: ${state.reason}")
                is WorkspaceLaunchUiState.ReadinessError -> Log.w(LOG_TAG, "Workspace is not ready")
                else -> Unit
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        launchViewModel.onHostDisposed(hostToken)
        serviceScope.cancel()
        viewModelStore.clear()
        super.onDestroy()
    }

    private companion object { const val LOG_TAG = "DwtPhysicalButton" }
}
