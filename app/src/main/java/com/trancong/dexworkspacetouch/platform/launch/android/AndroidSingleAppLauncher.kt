package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.platform.launch.bounds.BoundsCalculationResult
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsCalculator
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsConfig
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsSanity
import com.trancong.dexworkspacetouch.platform.launch.bounds.launchMarginPx
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import kotlinx.coroutines.CancellationException
import com.trancong.dexworkspacetouch.workspace.launcher.diagnostics.DiagnosticRect
import com.trancong.dexworkspacetouch.workspace.launcher.diagnostics.WorkspaceLaunchDiagnosticContext
import com.trancong.dexworkspacetouch.workspace.launcher.diagnostics.WorkspaceLaunchDiagnostics

class AndroidSingleAppLauncher(
    private val platform: SingleAppLaunchPlatform,
    private val boundsConfig: LaunchBoundsConfig = LaunchBoundsConfig(),
    private val diagnostics: WorkspaceLaunchDiagnostics = WorkspaceLaunchDiagnostics.None,
    private val clock: () -> Long = System::currentTimeMillis,
) : SingleAppLauncher {
    override suspend fun launch(target: AppLaunchTarget): SingleAppLaunchResult =
        launch(target, diagnosticContext = null)

    override suspend fun launch(
        target: AppLaunchTarget,
        diagnosticContext: WorkspaceLaunchDiagnosticContext?,
    ): SingleAppLaunchResult {
        val snapshot = platform.currentSnapshot()
        if (snapshot == null) {
            record(diagnosticContext, target, null, null, null, null, null, null,
                "DISPLAY_UNAVAILABLE")
            return target.failure(AppLaunchFailureReason.DISPLAY_UNAVAILABLE)
        }

        val verification = platform.verifyComponent(target.identity)
        val activityInfo = if (verification == ComponentVerificationResult.AVAILABLE) {
            platform.activityInfo(target.identity)
        } else null
        when (verification) {
            ComponentVerificationResult.AVAILABLE -> Unit
            ComponentVerificationResult.PACKAGE_MISSING -> {
                record(diagnosticContext, target, snapshot, null, null, null, null, null,
                    "APP_NOT_FOUND")
                return target.failure(AppLaunchFailureReason.APP_NOT_FOUND)
            }
            ComponentVerificationResult.ACTIVITY_MISSING -> {
                record(diagnosticContext, target, snapshot, null, null, null, null, null,
                    "ACTIVITY_NOT_FOUND")
                return target.failure(AppLaunchFailureReason.ACTIVITY_NOT_FOUND)
            }
            ComponentVerificationResult.SECURITY_RESTRICTION -> {
                record(diagnosticContext, target, snapshot, null, null, null, null, null,
                    "SECURITY_RESTRICTION")
                return target.failure(AppLaunchFailureReason.SECURITY_RESTRICTION)
            }
            ComponentVerificationResult.UNKNOWN -> {
                record(diagnosticContext, target, snapshot, null, null, null, null, null, "UNKNOWN")
                return target.failure(AppLaunchFailureReason.UNKNOWN)
            }
        }

        val marginPx = launchMarginPx(snapshot.density, boundsConfig)
        val boundsTrace = LaunchBoundsCalculator(marginPx).trace(target.bounds, snapshot.workArea)
        val beforeClamp = boundsTrace.beforeClamp.let { DiagnosticRect(it.left, it.top, it.right, it.bottom) }
        val pixelBounds = when (val boundsResult = boundsTrace.result) {
            is BoundsCalculationResult.Success -> boundsResult.bounds
            is BoundsCalculationResult.Failure -> {
                record(diagnosticContext, target, snapshot, activityInfo, beforeClamp, null,
                    marginPx, null, "LAUNCH_REJECTED")
                return target.failure(AppLaunchFailureReason.LAUNCH_REJECTED)
            }
        }
        if (!LaunchBoundsSanity.isWithinWorkArea(pixelBounds, snapshot.workArea)) {
            platform.reportRejectedBounds(snapshot, pixelBounds)
            record(diagnosticContext, target, snapshot, activityInfo, beforeClamp, pixelBounds,
                marginPx, null, "LAUNCH_REJECTED")
            return target.failure(AppLaunchFailureReason.LAUNCH_REJECTED)
        }

        val startedAt = clock()
        val startResult = try {
            platform.start(target, pixelBounds, snapshot.displayId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
        val completedAt = clock()
        record(diagnosticContext, target, snapshot, activityInfo, beforeClamp, pixelBounds,
            marginPx, startedAt, startResult.name, completedAt)
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

    private fun record(
        context: WorkspaceLaunchDiagnosticContext?,
        target: AppLaunchTarget,
        snapshot: com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot?,
        activityInfo: com.trancong.dexworkspacetouch.workspace.launcher.diagnostics.WorkspaceLaunchActivityInfo?,
        beforeClamp: DiagnosticRect?,
        requested: com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds?,
        marginPx: Int?,
        startedAt: Long?,
        result: String,
        completedAt: Long? = startedAt,
    ) {
        if (context == null) return
        runCatching {
            diagnostics.record(
                context, target, activityInfo, snapshot, beforeClamp, requested, marginPx,
                WORKSPACE_LAUNCH_INTENT_FLAGS,
                snapshot?.let { platform.launchDisplayId(it.displayId) },
                startedAt, completedAt, result,
            )
        }
    }
}
