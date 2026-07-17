package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.platform.launch.bounds.BoundsCalculationResult
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsCalculator
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsConfig
import com.trancong.dexworkspacetouch.platform.launch.bounds.launchMarginPx
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import kotlinx.coroutines.CancellationException

class AndroidSingleAppLauncher(
    private val platform: SingleAppLaunchPlatform,
    private val boundsConfig: LaunchBoundsConfig = LaunchBoundsConfig(),
) {
    suspend fun launch(target: AppLaunchTarget): SingleAppLaunchResult {
        val snapshot = platform.currentSnapshot()
            ?: return target.failure(AppLaunchFailureReason.DISPLAY_UNAVAILABLE)

        when (platform.verifyComponent(target.identity)) {
            ComponentVerificationResult.AVAILABLE -> Unit
            ComponentVerificationResult.PACKAGE_MISSING -> {
                return target.failure(AppLaunchFailureReason.APP_NOT_FOUND)
            }
            ComponentVerificationResult.ACTIVITY_MISSING -> {
                return target.failure(AppLaunchFailureReason.ACTIVITY_NOT_FOUND)
            }
            ComponentVerificationResult.SECURITY_RESTRICTION -> {
                return target.failure(AppLaunchFailureReason.SECURITY_RESTRICTION)
            }
            ComponentVerificationResult.UNKNOWN -> {
                return target.failure(AppLaunchFailureReason.UNKNOWN)
            }
        }

        val boundsResult = LaunchBoundsCalculator(
            marginPx = launchMarginPx(snapshot.density, boundsConfig),
        ).calculate(target.bounds, snapshot.workArea)
        val pixelBounds = when (boundsResult) {
            is BoundsCalculationResult.Success -> boundsResult.bounds
            is BoundsCalculationResult.Failure -> {
                return target.failure(AppLaunchFailureReason.LAUNCH_REJECTED)
            }
        }

        val startResult = try {
            platform.start(target, pixelBounds, snapshot.displayId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
        return when (startResult) {
            PlatformStartResult.SUCCESS -> SingleAppLaunchResult.Success(
                AppLaunchTargetResult(target),
            )
            PlatformStartResult.DISPLAY_UNAVAILABLE -> {
                target.failure(AppLaunchFailureReason.DISPLAY_UNAVAILABLE)
            }
            PlatformStartResult.ACTIVITY_NOT_FOUND -> {
                target.failure(AppLaunchFailureReason.ACTIVITY_NOT_FOUND)
            }
            PlatformStartResult.SECURITY_RESTRICTION -> {
                target.failure(AppLaunchFailureReason.SECURITY_RESTRICTION)
            }
            PlatformStartResult.LAUNCH_REJECTED -> {
                target.failure(AppLaunchFailureReason.LAUNCH_REJECTED)
            }
            PlatformStartResult.UNKNOWN -> target.failure(AppLaunchFailureReason.UNKNOWN)
        }
    }

    private fun AppLaunchTarget.failure(reason: AppLaunchFailureReason) =
        SingleAppLaunchResult.Failure(AppLaunchFailure(this, reason))
}
