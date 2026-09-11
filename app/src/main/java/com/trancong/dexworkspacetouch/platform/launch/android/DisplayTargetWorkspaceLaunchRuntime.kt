package com.trancong.dexworkspacetouch.platform.launch.android

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import com.trancong.dexworkspacetouch.platform.launch.bounds.DiagnosticPixelBounds
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkArea
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.HostWindowMode
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAdapter
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.LaunchEnvironmentCheck
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.LaunchEnvironmentFailure
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Workspace runtime for a process-owned overlay on an explicitly selected external display. */
class DisplayTargetWorkspaceLaunchRuntime(
    context: Context,
    displayProvider: () -> Display?,
) : WorkspaceLaunchRuntime {
    private val applicationContext = context.applicationContext
    private val platform = DisplayTargetSingleAppLaunchPlatform(
        applicationContext,
        displayProvider,
    )
    private val launcher = AndroidWorkspaceLauncher(AndroidSingleAppLauncher(platform))

    override fun checkEnvironment(): LaunchEnvironmentCheck =
        if (platform.currentSnapshot() != null) {
            LaunchEnvironmentCheck.Ready
        } else {
            LaunchEnvironmentCheck.Unavailable(LaunchEnvironmentFailure.WORK_AREA_UNAVAILABLE)
        }

    override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult =
        launcher.launch(request)
}

internal class DisplayTargetSingleAppLaunchPlatform(
    context: Context,
    private val displayProvider: () -> Display?,
) : SingleAppLaunchPlatform {
    private val applicationContext = context.applicationContext
    private val packageManagerAdapter = PackageManagerAdapter(applicationContext.packageManager)

    override fun currentSnapshot(): DisplayWorkAreaSnapshot? {
        val display = currentDisplay() ?: return null
        val displayContext = applicationContext.createDisplayContext(display)
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        } else {
            displayContext
        }
        val windowManager = windowContext.getSystemService(WindowManager::class.java)
        val (bounds, insets) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.maximumWindowMetrics
            val systemInsets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
            )
            metrics.bounds to intArrayOf(
                systemInsets.left,
                systemInsets.top,
                systemInsets.right,
                systemInsets.bottom,
            )
        } else {
            val legacy = legacyExternalDisplayWorkArea(display, displayContext) ?: return null
            android.graphics.Rect(0, 0, legacy.widthPx, legacy.heightPx) to intArrayOf(
                0,
                0,
                0,
                legacy.bottomInsetPx,
            )
        }
        if (bounds.width() <= 0 || bounds.height() <= 0) return null
        val rawBounds = DiagnosticPixelBounds(0, 0, bounds.width(), bounds.height())
        return DisplayWorkAreaSnapshot(
            displayId = display.displayId,
            workArea = DisplayWorkArea(
                widthPx = bounds.width(),
                heightPx = bounds.height(),
                insetLeftPx = insets[0],
                insetTopPx = insets[1],
                insetRightPx = insets[2],
                insetBottomPx = insets[3],
            ),
            rawDisplayBounds = rawBounds,
            hostWindowBounds = rawBounds,
            density = windowContext.resources.displayMetrics.density,
            hostWindowMode = HostWindowMode.MAXIMIZED,
        )
    }

    override fun verifyComponent(identity: AppIdentity): ComponentVerificationResult =
        verifyLaunchComponent(identity, packageManagerAdapter)

    override suspend fun start(
        target: AppLaunchTarget,
        bounds: PixelBounds,
        expectedDisplayId: Int,
    ): PlatformStartResult = try {
        withContext(Dispatchers.Main.immediate) {
            val display = currentDisplay()
                ?.takeIf { it.displayId == expectedDisplayId }
                ?: return@withContext PlatformStartResult.DISPLAY_UNAVAILABLE
            val activityName = target.identity.activityName
                ?: return@withContext PlatformStartResult.ACTIVITY_NOT_FOUND
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(target.identity.packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val options = ActivityOptions.makeBasic()
                .setLaunchBounds(bounds.toAndroidRect())
                .setLaunchDisplayId(display.displayId)
                .toBundle()
            applicationContext.startActivity(intent, options)
            PlatformStartResult.SUCCESS
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: ActivityNotFoundException) {
        PlatformStartResult.ACTIVITY_NOT_FOUND
    } catch (_: SecurityException) {
        PlatformStartResult.SECURITY_RESTRICTION
    } catch (_: IllegalArgumentException) {
        PlatformStartResult.DISPLAY_UNAVAILABLE
    } catch (_: IllegalStateException) {
        PlatformStartResult.LAUNCH_REJECTED
    } catch (exception: Exception) {
        Log.e("DexSingleAppLaunch", "Unexpected display-target launch failure", exception)
        PlatformStartResult.UNKNOWN
    }

    private fun currentDisplay(): Display? {
        return displayProvider().takeIf {
            it?.displayId != Display.DEFAULT_DISPLAY && it?.state == Display.STATE_ON
        }
    }
}
