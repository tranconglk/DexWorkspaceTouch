package com.trancong.dexworkspacetouch.feature.car.platform

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.Display
import com.trancong.dexworkspacetouch.feature.car.CarAction
import com.trancong.dexworkspacetouch.feature.car.CarActionError
import com.trancong.dexworkspacetouch.feature.car.CarActionExecutor
import com.trancong.dexworkspacetouch.feature.car.CarActionResult
import com.trancong.dexworkspacetouch.platform.launch.android.ActivityForegroundLaunchHost
import com.trancong.dexworkspacetouch.platform.launch.android.ForegroundLaunchHost

class AndroidCarActionExecutor internal constructor(
    private val appLaunchPlatform: CarAppLaunchPlatform,
    private val uriLaunchPlatform: CarUriLaunchPlatform,
    private val workspaceLaunchPlatform: CarWorkspaceLaunchPlatform,
) : CarActionExecutor {
    override suspend fun execute(action: CarAction): CarActionResult = when (action) {
        is CarAction.LaunchApp -> when (val result = appLaunchPlatform.launch(action.packageName)) {
            CarAppLaunchResult.Success -> CarActionResult.Success
            CarAppLaunchResult.Unavailable -> CarActionResult.Failure(CarActionError.AppUnavailable)
            is CarAppLaunchResult.Failed -> CarActionResult.Failure(
                CarActionError.ExecutionFailed(result.message),
            )
        }
        is CarAction.OpenUri -> {
            if (!CarUriPolicy.isSupported(action.uri)) {
                CarActionResult.Failure(CarActionError.UriUnavailable)
            } else {
                when (val result = uriLaunchPlatform.launch(action.uri)) {
                    CarUriLaunchResult.Success -> CarActionResult.Success
                    CarUriLaunchResult.Unavailable -> CarActionResult.Failure(
                        CarActionError.UriUnavailable,
                    )
                    is CarUriLaunchResult.Failed -> CarActionResult.Failure(
                        CarActionError.ExecutionFailed(result.message),
                    )
                }
            }
        }
        is CarAction.Workspace -> when (
            val result = workspaceLaunchPlatform.launch(action.workspaceId)
        ) {
            CarWorkspaceLaunchResult.Success -> CarActionResult.Success
            CarWorkspaceLaunchResult.WorkspaceUnavailable -> CarActionResult.Failure(
                CarActionError.WorkspaceUnavailable,
            )
            is CarWorkspaceLaunchResult.ExecutionFailed -> CarActionResult.Failure(
                CarActionError.ExecutionFailed(result.message),
            )
        }
        is CarAction.Delay -> CarActionResult.Failure(
            CarActionError.UnsupportedAction(action::class.simpleName.orEmpty()),
        )
    }

    companion object {
        fun create(
            activity: Activity,
            workspaceLaunchPlatform: CarWorkspaceLaunchPlatform,
        ): AndroidCarActionExecutor {
            val launchHost = ActivityForegroundLaunchHost(activity)
            return AndroidCarActionExecutor(
                appLaunchPlatform = ActivityCarAppLaunchPlatform(
                    launchHost = launchHost,
                    packageManager = activity.packageManager,
                ),
                uriLaunchPlatform = ActivityCarUriLaunchPlatform(
                    launchHost = launchHost,
                    packageManager = activity.packageManager,
                ),
                workspaceLaunchPlatform = workspaceLaunchPlatform,
            )
        }

        fun createForOverlay(
            context: Context,
            workspaceLaunchPlatform: CarWorkspaceLaunchPlatform = CarWorkspaceLaunchPlatform {
                CarWorkspaceLaunchResult.WorkspaceUnavailable
            },
            displayProvider: () -> Display?,
        ): AndroidCarActionExecutor {
            val applicationContext = context.applicationContext
            return AndroidCarActionExecutor(
                appLaunchPlatform = OverlayCarAppLaunchPlatform(
                    context = applicationContext,
                    displayProvider = displayProvider,
                ),
                uriLaunchPlatform = CarUriLaunchPlatform { CarUriLaunchResult.Unavailable },
                workspaceLaunchPlatform = workspaceLaunchPlatform,
            )
        }
    }
}

private class OverlayCarAppLaunchPlatform(
    private val context: Context,
    private val displayProvider: () -> Display?,
) : CarAppLaunchPlatform {
    private val packageManager = context.packageManager

    override fun launch(packageName: String): CarAppLaunchResult = try {
        val display = displayProvider()
            ?.takeIf {
                it.displayId != Display.DEFAULT_DISPLAY &&
                it.state == Display.STATE_ON &&
                    it.isValid
            }
            ?: return CarAppLaunchResult.Failed("Floating Dock display is unavailable.")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return CarAppLaunchResult.Unavailable
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(display.displayId)
            .toBundle()
        context.startActivity(launchIntent, options)
        CarAppLaunchResult.Success
    } catch (_: ActivityNotFoundException) {
        CarAppLaunchResult.Unavailable
    } catch (_: SecurityException) {
        CarAppLaunchResult.Failed("App launch was blocked by Android security policy.")
    } catch (_: IllegalArgumentException) {
        CarAppLaunchResult.Failed("Floating Dock display is unavailable.")
    } catch (_: IllegalStateException) {
        CarAppLaunchResult.Failed("Android could not start the app in the current state.")
    }
}

internal fun interface CarAppLaunchPlatform {
    fun launch(packageName: String): CarAppLaunchResult
}

internal sealed interface CarAppLaunchResult {
    data object Success : CarAppLaunchResult
    data object Unavailable : CarAppLaunchResult
    data class Failed(val message: String) : CarAppLaunchResult
}

internal fun interface CarUriLaunchPlatform {
    fun launch(uri: String): CarUriLaunchResult
}

internal sealed interface CarUriLaunchResult {
    data object Success : CarUriLaunchResult
    data object Unavailable : CarUriLaunchResult
    data class Failed(val message: String) : CarUriLaunchResult
}

internal object CarUriPolicy {
    private val schemePrefix = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun isSupported(uri: String): Boolean {
        if (!schemePrefix.containsMatchIn(uri)) return false
        return !uri.substringBefore(':').equals("intent", ignoreCase = true)
    }
}

private class ActivityCarAppLaunchPlatform(
    private val launchHost: ForegroundLaunchHost,
    private val packageManager: android.content.pm.PackageManager,
) : CarAppLaunchPlatform {
    override fun launch(packageName: String): CarAppLaunchResult = try {
        val activity = launchHost.activityOrNull()
            ?: return CarAppLaunchResult.Failed("Car launch host is unavailable.")
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: return CarAppLaunchResult.Unavailable
        activity.startActivity(launchIntent)
        CarAppLaunchResult.Success
    } catch (_: ActivityNotFoundException) {
        CarAppLaunchResult.Unavailable
    } catch (_: SecurityException) {
        CarAppLaunchResult.Failed("App launch was blocked by Android security policy.")
    } catch (_: IllegalStateException) {
        CarAppLaunchResult.Failed("Android could not start the app in the current state.")
    }
}

private class ActivityCarUriLaunchPlatform(
    private val launchHost: ForegroundLaunchHost,
    private val packageManager: android.content.pm.PackageManager,
) : CarUriLaunchPlatform {
    override fun launch(uri: String): CarUriLaunchResult {
        return try {
            val activity = launchHost.activityOrNull()
                ?: return CarUriLaunchResult.Failed("Car launch host is unavailable.")
            val parsedUri = Uri.parse(uri)
            val scheme = parsedUri.scheme
            if (scheme.isNullOrBlank() || scheme.equals("intent", ignoreCase = true)) {
                return CarUriLaunchResult.Unavailable
            }
            val viewIntent = Intent(Intent.ACTION_VIEW, parsedUri)
            if (viewIntent.resolveActivity(packageManager) == null) {
                return CarUriLaunchResult.Unavailable
            }
            activity.startActivity(viewIntent)
            CarUriLaunchResult.Success
        } catch (_: ActivityNotFoundException) {
            CarUriLaunchResult.Unavailable
        } catch (_: IllegalArgumentException) {
            CarUriLaunchResult.Unavailable
        } catch (_: SecurityException) {
            CarUriLaunchResult.Failed("URI launch was blocked by Android security policy.")
        } catch (_: IllegalStateException) {
            CarUriLaunchResult.Failed("Android could not open the URI in the current state.")
        }
    }
}
