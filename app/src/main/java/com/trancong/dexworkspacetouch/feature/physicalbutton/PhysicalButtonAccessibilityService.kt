package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Application
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.MainActivity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.WeakHashMap

/** Chỉ lọc hardware key; không đọc cây UI, click UI hoặc mô phỏng touch. */
class PhysicalButtonAccessibilityService : AccessibilityService() {
    // Chẩn đoán khác biệt giữa context Activity DeX và AccessibilityService.
    // Chỉ giữ weak key; display được xác thực lại khi launch.
    private val workspaceHostDisplayIds = WeakHashMap<Activity, Int>()
    private val displayContextDiagnostics = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            val activityDisplays = activity.getSystemService(DisplayManager::class.java)
            val serviceDisplays = getSystemService(DisplayManager::class.java)
            @Suppress("DEPRECATION")
            val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display
                else activity.windowManager.defaultDisplay
            if (activity is MainActivity && host != null) workspaceHostDisplayIds[activity] = host.displayId
            val activityHost = host?.let { activityDisplays.getDisplay(it.displayId) }
            val serviceHost = host?.let { serviceDisplays.getDisplay(it.displayId) }
            Log.i(LOG_TAG, "DisplayContext activity=${activity.javaClass.simpleName} host=${host?.let { "${it.displayId}:${it.state}:${it.flags}" }} activityHost=${activityHost?.let { "${it.displayId}:${it.state}:${it.flags}" }} serviceHost=${serviceHost?.let { "${it.displayId}:${it.state}:${it.flags}" }}")
            Log.i(LOG_TAG, "DisplayContext activityDisplays=${activityDisplays.getDisplays().joinToString { "${it.displayId}:${it.state}:${it.flags}" }} serviceDisplays=${serviceDisplays.getDisplays().joinToString { "${it.displayId}:${it.state}:${it.flags}" }}")
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) { workspaceHostDisplayIds.remove(activity) }
    }
    private lateinit var keyHandler: PhysicalButtonGestureController
    // Post dispatch sau khi onKeyEvent trả về để không giữ callback lọc phím.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val viewModelStore = ViewModelStore()
    private val hostToken = Any()
    private lateinit var launchViewModel: WorkspaceLaunchViewModel
    private lateinit var dispatcher: PhysicalButtonActionDispatcher
    private lateinit var preferences: PhysicalButtonPreferences
    private var dispatchJob: Job? = null
    private var targetDisplayId: Int? = null
    private var availableWorkspaceIds: Set<String> = emptySet()

    override fun onCreate() {
        super.onCreate()
        application.registerActivityLifecycleCallbacks(displayContextDiagnostics)
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
            packageCloseBackend = app.packageCloseBackend,
            canLaunch = {
                // Dùng nguyên gate hiện có, kể cả sau khi service sống lâu ở background.
                licenseCoordinator.bootstrap()
                val gate = licenseCoordinator.state.first { it !is LicenseGateUiState.Checking }
                val allowed = gate is LicenseGateUiState.Allowed
                Log.i(LOG_TAG, "Gate licenseAllowed=$allowed state=${gate.javaClass.simpleName}")
                allowed
            },
            launchWorkspace = { workspace ->
                // Không tự chọn giữa nhiều màn hình ngoài hoặc chuyển sang màn hình điện thoại.
                val availableDisplays = displayManager.getDisplays()
                Log.i(LOG_TAG, "Gate availableDisplays=${availableDisplays.joinToString { "${it.displayId}:${it.state}:${it.flags}" }}")
                val observedHostDisplays = workspaceHostDisplayIds.values.toSet()
                    .mapNotNull(displayManager::getDisplay)
                val requireObservedHost = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
                Log.i(LOG_TAG, "Gate observedHostDisplays=${observedHostDisplays.joinToString { "${it.displayId}:${it.state}:${it.flags}" }} requireObservedHost=$requireObservedHost")
                val selectedId = selectPhysicalButtonDisplayId(
                    availableDisplays.map { PhysicalButtonDisplaySnapshot(it.displayId, it.state, it.flags) },
                    observedHostDisplays.map { PhysicalButtonDisplaySnapshot(it.displayId, it.state, it.flags) },
                    requireObservedHost,
                )
                val display = selectedId?.let(displayManager::getDisplay)?.takeIf {
                    it.displayId == selectedId && it.displayId != Display.DEFAULT_DISPLAY &&
                        it.state == Display.STATE_ON && it.flags and Display.FLAG_PRIVATE == 0
                }
                Log.i(LOG_TAG, "Gate selectedDisplay=${display?.displayId} launching=${isLaunching()} carBusy=${app.carWorkflowExecutionArbiter.isRunning.value} productPhase=${app.embeddedProductRunGate.status.value.phase}")
                if (display == null || isLaunching() || app.carWorkflowExecutionArbiter.isRunning.value) false
                else {
                    val admitted = app.embeddedProductRunGate.tryDispatchClassic {
                        targetDisplayId = display.displayId
                        launchViewModel.launchWorkspace(workspace.toLibraryItem(), runtime, hostToken)
                    }
                    Log.i(LOG_TAG, "Gate classicAdmitted=$admitted")
                    admitted
                }
            },
        )
        keyHandler = PhysicalButtonGestureController(
            scope = serviceScope,
            configuration = { preferences.configuration.value },
            availableWorkspaceIds = { availableWorkspaceIds },
            bindingRequested = { preferences.bindingRequested.value },
            nowMillis = SystemClock::uptimeMillis,
            onGesture = ::onGesture,
        )
        serviceScope.launch {
            app.workspaceRepository.observeAll().collect { workspaces ->
                availableWorkspaceIds = workspaces.mapTo(mutableSetOf()) { it.id }
                keyHandler.invalidatePresses()
            }
        }
        serviceScope.launch {
            combine(preferences.configuration, preferences.bindingRequested) { _, _ -> Unit }
                .collect { keyHandler.invalidatePresses() }
        }
        serviceScope.launch {
            launchViewModelSnapshotLog()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        val decision = keyHandler.handle(
            event = PhysicalButtonKeyEvent(
                keyCode = event.keyCode,
                action = event.action,
                repeatCount = event.repeatCount,
                deviceId = event.deviceId,
                downTime = event.downTime,
                isExternal = device?.isExternal == true,
                deviceDescriptor = device?.descriptor,
                isVirtual = device?.isVirtual != false,
                eventTime = event.eventTime,
            ),
        )
        if (decision == PhysicalButtonKeyDecision.PASS_THROUGH) return false
        if (decision == PhysicalButtonKeyDecision.BIND_REMOTE) {
            if (preferences.captureRemote(device?.descriptor, device?.name)) {
                Log.i(LOG_TAG, "Remote linked device=${device?.name} deviceId=${event.deviceId} descriptor=${device?.descriptor}")
            }
            return true
        }
        return true
    }

    private fun onGesture(
        gesture: PhysicalButtonGesture,
        event: PhysicalButtonKeyEvent,
        configuration: PhysicalButtonConfiguration,
    ) {
        val workspaceId = configuration.workspaceForKey(event.keyCode)
        Log.i(LOG_TAG, "Gesture type=$gesture keyCode=${event.keyCode} workspaceId=$workspaceId deviceId=${event.deviceId}")
        if (gesture == PhysicalButtonGesture.SHORT_PRESS && (dispatchJob?.isActive == true || isLaunching())) return
        val job = serviceScope.launch {
            val result = dispatcher.dispatch(configuration.deviceDescriptor, event.keyCode, workspaceId,
                gesture, expectedConfiguration = configuration)
            val message = "Workspace gesture=$gesture keyCode=${event.keyCode} workspaceId=$workspaceId action=$result"
            when (result) {
                PhysicalButtonActionResult.CLOSE_PARTIAL,
                PhysicalButtonActionResult.CLOSE_BACKEND_UNAVAILABLE,
                PhysicalButtonActionResult.CLOSE_UNSUPPORTED -> Log.w(LOG_TAG, message)
                else -> Log.i(LOG_TAG, message)
            }
        }
        if (gesture == PhysicalButtonGesture.SHORT_PRESS) dispatchJob = job
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
    override fun onInterrupt() { preferences.cancelBinding() }

    override fun onDestroy() {
        application.unregisterActivityLifecycleCallbacks(displayContextDiagnostics)
        workspaceHostDisplayIds.clear()
        preferences.cancelBinding()
        keyHandler.clear()
        launchViewModel.onHostDisposed(hostToken)
        serviceScope.cancel()
        viewModelStore.clear()
        super.onDestroy()
    }

    private companion object { const val LOG_TAG = "DwtPhysicalButton" }
}

internal data class PhysicalButtonDisplaySnapshot(val id: Int, val state: Int, val flags: Int)

/** Một màn hình ngoài được liệt kê; legacy chỉ launch vào host DeX đã quan sát. */
internal fun selectPhysicalButtonDisplayId(
    availableDisplays: List<PhysicalButtonDisplaySnapshot>,
    observedHostDisplays: List<PhysicalButtonDisplaySnapshot>,
    requireObservedHost: Boolean,
): Int? {
    fun activeExternal(displays: List<PhysicalButtonDisplaySnapshot>) = displays.filter {
        it.id != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON &&
            it.flags and Display.FLAG_PRIVATE == 0
    }.distinctBy { it.id }
    val available = activeExternal(availableDisplays).singleOrNull() ?: return null
    return if (requireObservedHost) activeExternal(observedHostDisplays).singleOrNull()?.id
        else available.id
}
