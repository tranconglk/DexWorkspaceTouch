package com.trancong.dexworkspacetouch.feature.car.platform

import com.trancong.dexworkspacetouch.feature.car.CarAction
import com.trancong.dexworkspacetouch.feature.car.CarActionError
import com.trancong.dexworkspacetouch.feature.car.CarActionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCarActionExecutorTest {
    @Test
    fun launchablePackage_returnsSuccessAndRequestsExactPackage() = runBlocking {
        val platform = FakeCarAppLaunchPlatform(CarAppLaunchResult.Success)

        val result = createExecutor(appPlatform = platform).execute(
            CarAction.LaunchApp("com.example.maps"),
        )

        assertSame(CarActionResult.Success, result)
        assertEquals(listOf("com.example.maps"), platform.requestedPackages)
    }

    @Test
    fun missingPackage_returnsAppUnavailable() = runBlocking {
        assertAppUnavailable(CarAppLaunchResult.Unavailable)
    }

    @Test
    fun packageWithoutLaunchIntent_returnsAppUnavailable() = runBlocking {
        assertAppUnavailable(CarAppLaunchResult.Unavailable)
    }

    @Test
    fun launchFailure_returnsExecutionFailedInsteadOfCrashing() = runBlocking {
        val executor = createExecutor(
            appPlatform = FakeCarAppLaunchPlatform(CarAppLaunchResult.Failed("Launch blocked.")),
        )

        val result = executor.execute(CarAction.LaunchApp("com.example.blocked"))

        assertEquals(
            CarActionResult.Failure(CarActionError.ExecutionFailed("Launch blocked.")),
            result,
        )
    }

    @Test
    fun supportedUriSchemes_succeedAndPassExactUriToPlatform() = runBlocking {
        val platform = FakeCarUriLaunchPlatform(CarUriLaunchResult.Success)
        val executor = createExecutor(uriPlatform = platform)
        val uris = listOf(
            "https://example.com/path?q=car",
            "geo:10.8231,106.6297",
            "my-car-app://navigation/home",
        )

        uris.forEach { uri -> assertSame(CarActionResult.Success, executor.execute(CarAction.OpenUri(uri))) }

        assertEquals(uris, platform.requestedUris)
    }

    @Test
    fun missingScheme_returnsUriUnavailableWithoutLaunching() = runBlocking {
        assertRejectedUri("example.com/path")
    }

    @Test
    fun intentScheme_returnsUriUnavailableWithoutLaunching() = runBlocking {
        assertRejectedUri("InTeNt://scan/#Intent;scheme=zxing;end")
    }

    @Test
    fun uriWithoutHandlerOrActivityNotFound_returnsUriUnavailable() = runBlocking {
        val executor = createExecutor(
            uriPlatform = FakeCarUriLaunchPlatform(CarUriLaunchResult.Unavailable),
        )

        val result = executor.execute(CarAction.OpenUri("unknown-scheme://target"))

        assertEquals(CarActionResult.Failure(CarActionError.UriUnavailable), result)
    }

    @Test
    fun uriSecurityOrHostFailure_returnsExecutionFailed() = runBlocking {
        val executor = createExecutor(
            uriPlatform = FakeCarUriLaunchPlatform(CarUriLaunchResult.Failed("URI blocked.")),
        )

        val result = executor.execute(CarAction.OpenUri("https://example.com"))

        assertEquals(
            CarActionResult.Failure(CarActionError.ExecutionFailed("URI blocked.")),
            result,
        )
    }

    @Test
    fun workspace_delegatesExactIdAndMapsSuccess() = runBlocking {
        val platform = FakeCarWorkspaceLaunchPlatform(CarWorkspaceLaunchResult.Success)
        val executor = createExecutor(workspacePlatform = platform)

        val result = executor.execute(CarAction.Workspace("drive"))

        assertSame(CarActionResult.Success, result)
        assertEquals(listOf("drive"), platform.requestedWorkspaceIds)
    }

    @Test
    fun workspaceUnavailable_mapsToWorkspaceUnavailable() = runBlocking {
        val result = createExecutor(
            workspacePlatform = FakeCarWorkspaceLaunchPlatform(
                CarWorkspaceLaunchResult.WorkspaceUnavailable,
            ),
        ).execute(CarAction.Workspace("missing"))

        assertEquals(CarActionResult.Failure(CarActionError.WorkspaceUnavailable), result)
    }

    @Test
    fun workspaceExecutionFailure_mapsToExecutionFailed() = runBlocking {
        val result = createExecutor(
            workspacePlatform = FakeCarWorkspaceLaunchPlatform(
                CarWorkspaceLaunchResult.ExecutionFailed("Partial launch."),
            ),
        ).execute(CarAction.Workspace("drive"))

        assertEquals(
            CarActionResult.Failure(CarActionError.ExecutionFailed("Partial launch.")),
            result,
        )
    }

    @Test
    fun delay_isExplicitlyUnsupportedByExecutor() = runBlocking {
        val platform = FakeCarAppLaunchPlatform(CarAppLaunchResult.Success)

        val result = createExecutor(appPlatform = platform).execute(CarAction.Delay(0L))

        assertTrue((result as CarActionResult.Failure).error is CarActionError.UnsupportedAction)
        assertTrue(platform.requestedPackages.isEmpty())
    }

    private suspend fun assertAppUnavailable(platformResult: CarAppLaunchResult) {
        val executor = createExecutor(appPlatform = FakeCarAppLaunchPlatform(platformResult))

        val result = executor.execute(CarAction.LaunchApp("com.example.unavailable"))

        assertEquals(CarActionResult.Failure(CarActionError.AppUnavailable), result)
    }

    private suspend fun assertRejectedUri(uri: String) {
        val platform = FakeCarUriLaunchPlatform(CarUriLaunchResult.Success)
        val result = createExecutor(uriPlatform = platform).execute(CarAction.OpenUri(uri))

        assertEquals(CarActionResult.Failure(CarActionError.UriUnavailable), result)
        assertTrue(platform.requestedUris.isEmpty())
    }

    private fun createExecutor(
        appPlatform: CarAppLaunchPlatform = FakeCarAppLaunchPlatform(CarAppLaunchResult.Success),
        uriPlatform: CarUriLaunchPlatform = FakeCarUriLaunchPlatform(CarUriLaunchResult.Success),
        workspacePlatform: CarWorkspaceLaunchPlatform = FakeCarWorkspaceLaunchPlatform(
            CarWorkspaceLaunchResult.Success,
        ),
    ) = AndroidCarActionExecutor(appPlatform, uriPlatform, workspacePlatform)

    private class FakeCarAppLaunchPlatform(
        private val result: CarAppLaunchResult,
    ) : CarAppLaunchPlatform {
        val requestedPackages = mutableListOf<String>()

        override fun launch(packageName: String): CarAppLaunchResult {
            requestedPackages += packageName
            return result
        }
    }

    private class FakeCarUriLaunchPlatform(
        private val result: CarUriLaunchResult,
    ) : CarUriLaunchPlatform {
        val requestedUris = mutableListOf<String>()

        override fun launch(uri: String): CarUriLaunchResult {
            requestedUris += uri
            return result
        }
    }

    private class FakeCarWorkspaceLaunchPlatform(
        private val result: CarWorkspaceLaunchResult,
    ) : CarWorkspaceLaunchPlatform {
        val requestedWorkspaceIds = mutableListOf<String>()

        override suspend fun launch(workspaceId: String): CarWorkspaceLaunchResult {
            requestedWorkspaceIds += workspaceId
            return result
        }
    }
}
