package com.trancong.dexworkspacetouch.workspace.launcher.diagnostics

import com.trancong.dexworkspacetouch.platform.launch.bounds.DiagnosticPixelBounds
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest

data class WorkspaceLaunchDiagnosticSession(
    val sessionId: String,
    val timestampEpochMillis: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val device: WorkspaceLaunchDiagnosticDevice,
    val workspaceId: String,
    val workspaceName: String,
    val appCount: Int,
    val apps: List<WorkspaceLaunchDiagnosticApp> = emptyList(),
)

data class WorkspaceLaunchDiagnosticDevice(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
)

data class WorkspaceLaunchDiagnosticDisplay(
    val displayId: Int,
    val name: String?,
    val type: Int?,
    val logicalWidthPx: Int,
    val logicalHeightPx: Int,
    val modeWidthPx: Int?,
    val modeHeightPx: Int?,
    val density: Float,
    val workArea: DiagnosticRect,
    val resolvedInsets: DiagnosticInsets,
    val selectedInsetSource: String,
)

data class WorkspaceLaunchActivityInfo(
    val launchMode: Int?,
    val documentLaunchMode: Int?,
    val taskAffinity: String?,
    val resizeMode: Int?,
)

data class WorkspaceLaunchDiagnosticApp(
    val sequenceIndex: Int,
    val packageName: String,
    val activityName: String?,
    val activityInfo: WorkspaceLaunchActivityInfo?,
    val normalizedBounds: NormalizedBounds,
    val display: WorkspaceLaunchDiagnosticDisplay?,
    val beforeClampBounds: DiagnosticRect?,
    val requestedPixelBounds: DiagnosticRect?,
    val marginPx: Int?,
    val intentFlags: Int,
    val launchDisplayId: Int?,
    val launchStartedAtEpochMillis: Long?,
    val launchCompletedAtEpochMillis: Long?,
    val result: String,
)

data class DiagnosticRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

data class DiagnosticInsets(val left: Int, val top: Int, val right: Int, val bottom: Int)

data class WorkspaceLaunchDiagnosticContext(val sessionId: String, val sequenceIndex: Int)

interface WorkspaceLaunchDiagnostics {
    fun begin(request: WorkspaceLaunchRequest): String?

    fun record(
        context: WorkspaceLaunchDiagnosticContext,
        target: AppLaunchTarget,
        activityInfo: WorkspaceLaunchActivityInfo?,
        snapshot: DisplayWorkAreaSnapshot?,
        beforeClampBounds: DiagnosticRect?,
        requestedPixelBounds: PixelBounds?,
        marginPx: Int?,
        intentFlags: Int,
        launchDisplayId: Int?,
        launchStartedAtEpochMillis: Long?,
        launchCompletedAtEpochMillis: Long?,
        result: String,
    )

    fun finish(sessionId: String)

    data object None : WorkspaceLaunchDiagnostics {
        override fun begin(request: WorkspaceLaunchRequest): String? = null
        override fun record(context: WorkspaceLaunchDiagnosticContext, target: AppLaunchTarget,
            activityInfo: WorkspaceLaunchActivityInfo?, snapshot: DisplayWorkAreaSnapshot?,
            beforeClampBounds: DiagnosticRect?, requestedPixelBounds: PixelBounds?, marginPx: Int?,
            intentFlags: Int, launchDisplayId: Int?, launchStartedAtEpochMillis: Long?,
            launchCompletedAtEpochMillis: Long?, result: String) = Unit
        override fun finish(sessionId: String) = Unit
    }
}

fun DisplayWorkAreaSnapshot.toDiagnosticDisplay() = WorkspaceLaunchDiagnosticDisplay(
    displayId, displayName, displayType, rawDisplayBounds.width, rawDisplayBounds.height,
    displayModeWidthPx, displayModeHeightPx, density,
    DiagnosticRect(workArea.originX, workArea.originY, workArea.usableRight, workArea.usableBottom),
    DiagnosticInsets(workArea.insetLeftPx, workArea.insetTopPx, workArea.insetRightPx, workArea.insetBottomPx),
    selectedInsetSource.name,
)

fun DiagnosticPixelBounds.toDiagnosticRect() = DiagnosticRect(left, top, right, bottom)
fun PixelBounds.toDiagnosticRect() = DiagnosticRect(left, top, right, bottom)
