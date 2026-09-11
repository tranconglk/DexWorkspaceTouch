package com.trancong.dexworkspacetouch.feature.car.platform

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.MainActivity
import com.trancong.dexworkspacetouch.feature.car.CarAction
import com.trancong.dexworkspacetouch.feature.car.CarActionError
import com.trancong.dexworkspacetouch.feature.car.CarActionResult
import com.trancong.dexworkspacetouch.platform.launch.android.AndroidWorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyDisplayWorkAreaReferenceStore
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assume.assumeNotNull
import org.junit.Test

class AndroidCarActionExecutorDeviceTest {
    @Test
    fun installedLaunchableTargetPackage_succeeds() = runBlocking {
        withExternalMainActivity { activity ->
            val result = executor(activity).execute(
                CarAction.LaunchApp(activity.packageName),
            )
            assertSame(CarActionResult.Success, result)
        }
    }

    @Test
    fun installedTestPackageWithoutLauncher_isUnavailable() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        withExternalMainActivity { activity ->
            val result = executor(activity).execute(
                CarAction.LaunchApp(instrumentation.context.packageName),
            )
            assertEquals(CarActionResult.Failure(CarActionError.AppUnavailable), result)
        }
    }

    @Test
    fun missingPackage_isUnavailable() = runBlocking {
        withExternalMainActivity { activity ->
            val result = executor(activity).execute(
                CarAction.LaunchApp("com.trancong.dexworkspacetouch.package.does.not.exist"),
            )
            assertEquals(CarActionResult.Failure(CarActionError.AppUnavailable), result)
        }
    }

    @Test
    fun safeHttpsUriWithHandler_succeedsOnExternalHost() = runBlocking {
        withExternalMainActivity { activity ->
            val result = executor(activity).execute(
                CarAction.OpenUri("https://example.com"),
            )
            assertSame(CarActionResult.Success, result)
        }
    }

    @Test
    fun unknownUriScheme_isUnavailableWithoutCrash() = runBlocking {
        withExternalMainActivity { activity ->
            val result = executor(activity).execute(
                CarAction.OpenUri("dex-workspace-touch-no-handler://smoke-test"),
            )
            assertEquals(CarActionResult.Failure(CarActionError.UriUnavailable), result)
        }
    }

    @Test
    fun missingWorkspaceId_isUnavailableOnExternalHost() = runBlocking {
        withExternalMainActivity { activity ->
            val result = workspaceExecutor(activity).execute(
                CarAction.Workspace("workspace-that-does-not-exist"),
            )
            assertEquals(CarActionResult.Failure(CarActionError.WorkspaceUnavailable), result)
        }
    }

    @Test
    fun savedReadyWorkspace_usesExistingLauncherOnExternalHost() = runBlocking {
        withExternalMainActivity { activity ->
            val application = activity.application as DexWorkspaceTouchApplication
            val catalog = installedAppCatalog(activity)
            val requestFactory = WorkspaceLaunchRequestFactory(catalog)
            val workspace = application.workspaceRepository.observeAll().first().firstOrNull {
                it.name == "Car test" &&
                    requestFactory.create(it.id, it.name, it.canvas) is LaunchReadiness.Ready
            }
            assumeNotNull(workspace)

            val result = workspaceExecutor(activity, requestFactory).execute(
                CarAction.Workspace(requireNotNull(workspace).id),
            )

            assertSame(CarActionResult.Success, result)
        }
    }

    private fun executor(activity: Activity): AndroidCarActionExecutor =
        AndroidCarActionExecutor.create(activity) { CarWorkspaceLaunchResult.Success }

    private fun workspaceExecutor(
        activity: Activity,
        requestFactory: WorkspaceLaunchRequestFactory = WorkspaceLaunchRequestFactory(
            installedAppCatalog(activity),
        ),
    ): AndroidCarActionExecutor {
        val application = activity.application as DexWorkspaceTouchApplication
        return AndroidCarActionExecutor.create(
            activity,
            RepositoryCarWorkspaceLaunchPlatform(
                repository = application.workspaceRepository,
                requestFactory = requestFactory,
                runtime = AndroidWorkspaceLaunchRuntime(
                    activity,
                    LegacyDisplayWorkAreaReferenceStore(),
                ),
            ),
        )
    }

    private fun installedAppCatalog(activity: Activity) = DefaultInstalledAppCatalog(
        AndroidInstalledAppDataSource.create(activity.applicationContext),
    )

    private suspend fun withExternalMainActivity(block: suspend (Activity) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val externalDisplay = targetContext.getSystemService(DisplayManager::class.java)
            .displays
            .firstOrNull { it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON }
        assumeNotNull(externalDisplay)
        val intent = Intent(targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(requireNotNull(externalDisplay).displayId)
            .toBundle()
        val activity = instrumentation.startActivitySync(intent, options)
        try {
            assertEquals(requireNotNull(externalDisplay).displayId, activity.display?.displayId)
            block(activity)
        } finally {
            activity.finish()
        }
    }
}
