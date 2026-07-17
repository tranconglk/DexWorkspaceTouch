package com.trancong.dexworkspacetouch.platform.launch.android

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaProvider
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAdapter
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ActivitySingleAppLaunchPlatform(
    private val launchHost: ForegroundLaunchHost,
    private val packageManagerAdapter: PackageManagerAdapter,
    private val workAreaProviderFactory: (Activity) -> DisplayWorkAreaProvider =
        ::ActivityDisplayWorkAreaProvider,
) : SingleAppLaunchPlatform {
    override fun currentSnapshot(): DisplayWorkAreaSnapshot? {
        val activity = launchHost.activityOrNull() ?: return null
        val activityDisplayId = activity.externalDisplayIdOrNull() ?: return null
        val snapshot = workAreaProviderFactory(activity).getSnapshot() ?: return null
        return snapshot.takeIf { it.displayId == activityDisplayId }
    }

    override fun verifyComponent(identity: AppIdentity): ComponentVerificationResult {
        if (identity.activityName == null) return ComponentVerificationResult.ACTIVITY_MISSING
        return try {
            packageManagerAdapter.verifyPackageExists(identity.packageName)
            try {
                packageManagerAdapter.verifyActivityExists(identity)
            } catch (_: PackageManager.NameNotFoundException) {
                return ComponentVerificationResult.ACTIVITY_MISSING
            }
            if (packageManagerAdapter.canResolveLauncherActivity(identity)) {
                ComponentVerificationResult.AVAILABLE
            } else {
                ComponentVerificationResult.ACTIVITY_MISSING
            }
        } catch (_: PackageManager.NameNotFoundException) {
            ComponentVerificationResult.PACKAGE_MISSING
        } catch (exception: SecurityException) {
            Log.w(LOG_TAG, "Component verification blocked by security policy", exception)
            ComponentVerificationResult.SECURITY_RESTRICTION
        } catch (exception: Exception) {
            Log.e(LOG_TAG, "Component verification failed", exception)
            ComponentVerificationResult.UNKNOWN
        }
    }

    override suspend fun start(
        target: AppLaunchTarget,
        bounds: PixelBounds,
        expectedDisplayId: Int,
    ): PlatformStartResult = try {
        withContext(Dispatchers.Main.immediate) {
            val activity = launchHost.activityOrNull()
                ?: return@withContext PlatformStartResult.DISPLAY_UNAVAILABLE
            if (activity.externalDisplayIdOrNull() != expectedDisplayId) {
                return@withContext PlatformStartResult.DISPLAY_UNAVAILABLE
            }
            val activityName = target.identity.activityName
                ?: return@withContext PlatformStartResult.ACTIVITY_NOT_FOUND
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(target.identity.packageName, activityName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
            val rect = bounds.toAndroidRect()
            val options = ActivityOptions.makeBasic().setLaunchBounds(rect)
            Log.d(
                LOG_TAG,
                "Launching ${target.identity.packageName}/$activityName " +
                    "on displayId=$expectedDisplayId bounds=$rect",
            )
            activity.startActivity(intent, options.toBundle())
            PlatformStartResult.SUCCESS
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: ActivityNotFoundException) {
        Log.w(LOG_TAG, "Launch activity was not found", exception)
        PlatformStartResult.ACTIVITY_NOT_FOUND
    } catch (exception: SecurityException) {
        Log.w(LOG_TAG, "Launch blocked by security policy", exception)
        PlatformStartResult.SECURITY_RESTRICTION
    } catch (exception: IllegalArgumentException) {
        Log.w(LOG_TAG, "Launch bounds or options were rejected", exception)
        PlatformStartResult.LAUNCH_REJECTED
    } catch (exception: UnsupportedOperationException) {
        Log.w(LOG_TAG, "Launch bounds are not supported", exception)
        PlatformStartResult.LAUNCH_REJECTED
    } catch (exception: Exception) {
        Log.e(LOG_TAG, "Unexpected launch failure", exception)
        PlatformStartResult.UNKNOWN
    }

    private companion object {
        const val LOG_TAG = "DexSingleAppLaunch"
    }
}
