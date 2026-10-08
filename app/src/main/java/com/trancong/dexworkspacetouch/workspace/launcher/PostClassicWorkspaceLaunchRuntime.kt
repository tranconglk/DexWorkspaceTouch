package com.trancong.dexworkspacetouch.workspace.launcher

import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.*

/** One shared post-Success boundary for Library, Car and Dock workspace launches. */
class PostClassicWorkspaceLaunchRuntime(
    private val runtime: WorkspaceLaunchRuntime,
    private val snapshot: () -> DisplayWorkAreaSnapshot?,
    private val onStarted: (String) -> Unit,
    private val onSuccess: (WorkspaceLaunchRequest, DisplayWorkAreaSnapshot) -> Unit,
    private val onPrepared: (DisplayWorkAreaSnapshot?) -> Unit = {},
) : WorkspaceLaunchRuntime {
    override fun checkEnvironment() = runtime.checkEnvironment()

    override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult {
        runCatching { onStarted(request.workspaceId) }
        val before = runCatching(snapshot).getOrNull()
        runCatching { onPrepared(before) }
        val result = runtime.launch(request)
        if (result is WorkspaceLaunchResult.Success && before != null &&
            before.sameRepairGeometry(runCatching(snapshot).getOrNull())) {
            runCatching { onSuccess(request, before) }
        }
        return result
    }
}

/** Host-window diagnostics/inset provenance do not change the display geometry used by Repair. */
internal fun DisplayWorkAreaSnapshot.sameRepairGeometry(other: DisplayWorkAreaSnapshot?): Boolean =
    other != null && displayId == other.displayId && workArea == other.workArea &&
        rawDisplayBounds == other.rawDisplayBounds && density == other.density
