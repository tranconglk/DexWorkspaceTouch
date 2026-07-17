package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.platform.launch.bounds.DiagnosticPixelBounds
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkArea
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.HostWindowMode
import com.trancong.dexworkspacetouch.platform.launch.bounds.LaunchBoundsConfig
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidSingleAppLauncherTest {
    private val target = AppLaunchTarget(
        identity = AppIdentity("com.example.app", "com.example.app.MainActivity"),
        bounds = NormalizedBounds.FullCanvas,
        order = 0,
    )

    @Test
    fun `success retains exact target and calculated bounds`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform()

        val result = AndroidSingleAppLauncher(platform).launch(target)

        assertSame(
            target,
            (result as SingleAppLaunchResult.Success).launchedTarget.target,
        )
        assertEquals(PixelBounds(8, 8, 1912, 1192), platform.startedBounds)
        assertEquals(2, platform.startedDisplayId)
    }

    @Test
    fun `missing package maps to app not found`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform(
            verification = ComponentVerificationResult.PACKAGE_MISSING,
        )

        assertFailureReason(
            AppLaunchFailureReason.APP_NOT_FOUND,
            AndroidSingleAppLauncher(platform).launch(target),
        )
        assertFalse(platform.startCalled)
    }

    @Test
    fun `missing or unresolvable activity maps to activity not found`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform(
            verification = ComponentVerificationResult.ACTIVITY_MISSING,
        )

        assertFailureReason(
            AppLaunchFailureReason.ACTIVITY_NOT_FOUND,
            AndroidSingleAppLauncher(platform).launch(target),
        )
    }

    @Test
    fun `missing display snapshot maps to display unavailable`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform(snapshot = null)

        assertFailureReason(
            AppLaunchFailureReason.DISPLAY_UNAVAILABLE,
            AndroidSingleAppLauncher(platform).launch(target),
        )
        assertFalse(platform.verifyCalled)
    }

    @Test
    fun `display disappearing immediately before start maps unavailable`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform(
            startResult = PlatformStartResult.DISPLAY_UNAVAILABLE,
        )

        assertFailureReason(
            AppLaunchFailureReason.DISPLAY_UNAVAILABLE,
            AndroidSingleAppLauncher(platform).launch(target),
        )
    }

    @Test
    fun `insufficient bounds map to launch rejected without starting`() = runBlocking {
        val tinySnapshot = snapshot(workArea = DisplayWorkArea(3, 3), density = 1f)
        val platform = FakeSingleAppLaunchPlatform(snapshot = tinySnapshot)

        assertFailureReason(
            AppLaunchFailureReason.LAUNCH_REJECTED,
            AndroidSingleAppLauncher(platform).launch(target),
        )
        assertFalse(platform.startCalled)
    }

    @Test
    fun `security verification maps to security restriction`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform(
            verification = ComponentVerificationResult.SECURITY_RESTRICTION,
        )

        assertFailureReason(
            AppLaunchFailureReason.SECURITY_RESTRICTION,
            AndroidSingleAppLauncher(platform).launch(target),
        )
    }

    @Test
    fun `security start failure maps to security restriction`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform(
            startResult = PlatformStartResult.SECURITY_RESTRICTION,
        )

        assertFailureReason(
            AppLaunchFailureReason.SECURITY_RESTRICTION,
            AndroidSingleAppLauncher(platform).launch(target),
        )
    }

    @Test
    fun `unknown platform failure maps to unknown without raw exception`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform(startResult = PlatformStartResult.UNKNOWN)

        val result = AndroidSingleAppLauncher(platform).launch(target)
            as SingleAppLaunchResult.Failure

        assertEquals(AppLaunchFailureReason.UNKNOWN, result.failure.reason)
        assertEquals(null, result.failure.technicalMessage)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation from platform is propagated`() {
        runBlocking {
            val platform = FakeSingleAppLaunchPlatform(throwCancellation = true)

            AndroidSingleAppLauncher(platform).launch(target)
        }
    }

    @Test
    fun `zero margin configuration is honored`() = runBlocking {
        val platform = FakeSingleAppLaunchPlatform()

        AndroidSingleAppLauncher(
            platform,
            boundsConfig = LaunchBoundsConfig(marginDp = 0f),
        ).launch(target)

        assertEquals(PixelBounds(0, 0, 1920, 1200), platform.startedBounds)
    }

    private fun assertFailureReason(
        expected: AppLaunchFailureReason,
        result: SingleAppLaunchResult,
    ) {
        assertTrue(result is SingleAppLaunchResult.Failure)
        result as SingleAppLaunchResult.Failure
        assertSame(target, result.failure.target)
        assertEquals(expected, result.failure.reason)
    }

    private class FakeSingleAppLaunchPlatform(
        private val snapshot: DisplayWorkAreaSnapshot? = snapshot(),
        private val verification: ComponentVerificationResult =
            ComponentVerificationResult.AVAILABLE,
        private val startResult: PlatformStartResult = PlatformStartResult.SUCCESS,
        private val throwCancellation: Boolean = false,
    ) : SingleAppLaunchPlatform {
        var verifyCalled = false
        var startCalled = false
        var startedBounds: PixelBounds? = null
        var startedDisplayId: Int? = null

        override fun currentSnapshot(): DisplayWorkAreaSnapshot? = snapshot

        override fun verifyComponent(identity: AppIdentity): ComponentVerificationResult {
            verifyCalled = true
            return verification
        }

        override suspend fun start(
            target: AppLaunchTarget,
            bounds: PixelBounds,
            expectedDisplayId: Int,
        ): PlatformStartResult {
            startCalled = true
            startedBounds = bounds
            startedDisplayId = expectedDisplayId
            if (throwCancellation) throw CancellationException("test cancellation")
            return startResult
        }
    }

    private companion object {
        fun snapshot(
            workArea: DisplayWorkArea = DisplayWorkArea(1920, 1200),
            density: Float = 1f,
        ) = DisplayWorkAreaSnapshot(
            displayId = 2,
            workArea = workArea,
            rawDisplayBounds = DiagnosticPixelBounds(0, 0, workArea.widthPx, workArea.heightPx),
            hostWindowBounds = DiagnosticPixelBounds(0, 0, 1920, 1200),
            density = density,
            hostWindowMode = HostWindowMode.MAXIMIZED,
        )
    }
}
