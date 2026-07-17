package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget

interface SingleAppLaunchPlatform {
    fun currentSnapshot(): DisplayWorkAreaSnapshot?

    fun verifyComponent(identity: AppIdentity): ComponentVerificationResult

    suspend fun start(
        target: AppLaunchTarget,
        bounds: PixelBounds,
        expectedDisplayId: Int,
    ): PlatformStartResult
}

enum class ComponentVerificationResult {
    AVAILABLE,
    PACKAGE_MISSING,
    ACTIVITY_MISSING,
    SECURITY_RESTRICTION,
    UNKNOWN,
}

enum class PlatformStartResult {
    SUCCESS,
    DISPLAY_UNAVAILABLE,
    ACTIVITY_NOT_FOUND,
    SECURITY_RESTRICTION,
    LAUNCH_REJECTED,
    UNKNOWN,
}
