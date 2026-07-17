package com.trancong.dexworkspacetouch.platform.launch.android

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidWorkspaceLauncherTest {
    @Test
    fun `one target succeeds without delay`() = runBlocking {
        val fixture = fixture()
        val result = fixture.launcher.launch(request(target(0)))

        assertEquals(listOf(0), result.launchedOrders())
        assertEquals(emptyList<Long>(), fixture.delay.waited)
    }

    @Test
    fun `multiple targets launch in ascending order`() = runBlocking {
        val fixture = fixture()
        fixture.launcher.launch(request(target(7), target(1), target(4)))

        assertEquals(listOf(1, 4, 7), fixture.single.calls.map { it.order })
    }

    @Test
    fun `delay occurs exactly between targets`() = runBlocking {
        val fixture = fixture(policy = LaunchSequencingPolicy(250))
        fixture.launcher.launch(request(target(0), target(1), target(2)))

        assertEquals(listOf(250L, 250L), fixture.delay.waited)
    }

    @Test
    fun `there is no delay after final target`() = runBlocking {
        val fixture = fixture()
        fixture.launcher.launch(request(target(0), target(1)))

        assertEquals(1, fixture.delay.waited.size)
    }

    @Test
    fun `zero delay is supported between targets`() = runBlocking {
        val fixture = fixture(policy = LaunchSequencingPolicy(0))
        fixture.launcher.launch(request(target(0), target(1)))

        assertEquals(listOf(0L), fixture.delay.waited)
    }

    @Test fun `app not found continues sequence`() = assertContinues(AppLaunchFailureReason.APP_NOT_FOUND)
    @Test fun `activity not found continues sequence`() = assertContinues(AppLaunchFailureReason.ACTIVITY_NOT_FOUND)
    @Test fun `security restriction continues sequence`() = assertContinues(AppLaunchFailureReason.SECURITY_RESTRICTION)
    @Test fun `launch rejected continues sequence`() = assertContinues(AppLaunchFailureReason.LAUNCH_REJECTED)
    @Test fun `unknown failure continues sequence`() = assertContinues(AppLaunchFailureReason.UNKNOWN)

    @Test
    fun `mixed outcomes aggregate partial success`() = runBlocking {
        val fixture = fixture(results = mapOf(1 to failure(1, AppLaunchFailureReason.APP_NOT_FOUND)))

        val result = fixture.launcher.launch(request(target(0), target(1), target(2)))

        assertTrue(result is WorkspaceLaunchResult.PartialSuccess)
        assertEquals(listOf(0, 2), result.launchedOrders())
        assertEquals(listOf(1), result.failedOrders())
    }

    @Test
    fun `all failed targets aggregate failure`() = runBlocking {
        val fixture = fixture(results = mapOf(
            0 to failure(0, AppLaunchFailureReason.APP_NOT_FOUND),
            1 to failure(1, AppLaunchFailureReason.UNKNOWN),
        ))

        val result = fixture.launcher.launch(request(target(0), target(1)))

        assertTrue(result is WorkspaceLaunchResult.Failure)
        assertEquals(listOf(0, 1), result.failedOrders())
    }

    @Test
    fun `display unavailable stops sequence without trailing delay`() = runBlocking {
        val fixture = fixture(results = mapOf(
            1 to failure(1, AppLaunchFailureReason.DISPLAY_UNAVAILABLE),
        ))

        fixture.launcher.launch(request(target(0), target(1), target(2)))

        assertEquals(listOf(0, 1), fixture.single.calls.map { it.order })
        assertEquals(listOf(400L), fixture.delay.waited)
    }

    @Test
    fun `pending targets are marked display unavailable in order`() = runBlocking {
        val fixture = fixture(results = mapOf(
            1 to failure(1, AppLaunchFailureReason.DISPLAY_UNAVAILABLE),
        ))

        val result = fixture.launcher.launch(request(target(3), target(1), target(2)))

        assertEquals(listOf(1, 2, 3), result.failedOrders())
        assertEquals(
            listOf(
                AppLaunchFailureReason.DISPLAY_UNAVAILABLE,
                AppLaunchFailureReason.DISPLAY_UNAVAILABLE,
                AppLaunchFailureReason.DISPLAY_UNAVAILABLE,
            ),
            result.failures().map { it.reason },
        )
        assertEquals(
            "Display became unavailable before launch.",
            result.failures()[1].technicalMessage,
        )
    }

    @Test
    fun `duplicate identity is launched for every target`() = runBlocking {
        val identity = AppIdentity("com.example.same", "com.example.same.Main")
        val fixture = fixture()

        fixture.launcher.launch(request(target(0, identity), target(1, identity)))

        assertEquals(2, fixture.single.calls.size)
    }

    @Test
    fun `cancellation before first target is rethrown`() {
        val fixture = fixture()
        assertThrows(CancellationException::class.java) {
            runBlocking {
                val cancelled = Job().apply { cancel() }
                withContext(cancelled) { fixture.launcher.launch(request(target(0))) }
            }
        }
        assertTrue(fixture.single.calls.isEmpty())
    }

    @Test
    fun `cancellation after a launch produces no aggregate result`() {
        val fixture = fixture(cancelAfterOrder = 0)
        assertThrows(CancellationException::class.java) {
            runBlocking { fixture.launcher.launch(request(target(0), target(1))) }
        }
        assertEquals(listOf(0), fixture.single.calls.map { it.order })
        assertTrue(fixture.delay.waited.isEmpty())
    }

    @Test
    fun `cancellation from delay is rethrown`() {
        val fixture = fixture(delay = FakeDelay(throwsCancellation = true))
        assertThrows(CancellationException::class.java) {
            runBlocking { fixture.launcher.launch(request(target(0), target(1))) }
        }
        assertEquals(listOf(0), fixture.single.calls.map { it.order })
    }

    @Test
    fun `result lists keep deterministic target order`() = runBlocking {
        val fixture = fixture(results = mapOf(
            2 to failure(2, AppLaunchFailureReason.UNKNOWN),
            6 to failure(6, AppLaunchFailureReason.APP_NOT_FOUND),
        ))

        val result = fixture.launcher.launch(request(target(6), target(4), target(2), target(0)))

        assertEquals(listOf(0, 4), result.launchedOrders())
        assertEquals(listOf(2, 6), result.failedOrders())
    }

    @Test
    fun `policy rejects delays outside zero through five seconds`() {
        assertThrows(IllegalArgumentException::class.java) { LaunchSequencingPolicy(-1) }
        assertThrows(IllegalArgumentException::class.java) { LaunchSequencingPolicy(5_001) }
        assertEquals(5_000, LaunchSequencingPolicy(5_000).delayBetweenTargetsMs)
    }

    private fun assertContinues(reason: AppLaunchFailureReason) = runBlocking {
        val fixture = fixture(results = mapOf(0 to failure(0, reason)))

        val result = fixture.launcher.launch(request(target(0), target(1)))

        assertEquals(listOf(0, 1), fixture.single.calls.map { it.order })
        assertTrue(result is WorkspaceLaunchResult.PartialSuccess)
    }

    private fun fixture(
        results: Map<Int, SingleAppLaunchResult> = emptyMap(),
        policy: LaunchSequencingPolicy = LaunchSequencingPolicy(),
        delay: FakeDelay = FakeDelay(),
        cancelAfterOrder: Int? = null,
    ): Fixture {
        val single = FakeSingleAppLauncher(results, cancelAfterOrder)
        return Fixture(
            launcher = AndroidWorkspaceLauncher(
                singleAppLauncher = single,
                sequencingPolicy = policy,
                launchDelay = delay,
                logger = WorkspaceLaunchLogger.None,
            ),
            single = single,
            delay = delay,
        )
    }

    private data class Fixture(
        val launcher: AndroidWorkspaceLauncher,
        val single: FakeSingleAppLauncher,
        val delay: FakeDelay,
    )

    private class FakeSingleAppLauncher(
        private val results: Map<Int, SingleAppLaunchResult>,
        private val cancelAfterOrder: Int?,
    ) : SingleAppLauncher {
        val calls = mutableListOf<AppLaunchTarget>()

        override suspend fun launch(target: AppLaunchTarget): SingleAppLaunchResult {
            calls += target
            if (target.order == cancelAfterOrder) currentCoroutineContext().cancel()
            return results[target.order]
                ?: SingleAppLaunchResult.Success(AppLaunchTargetResult(target))
        }
    }

    private class FakeDelay(private val throwsCancellation: Boolean = false) : LaunchDelay {
        val waited = mutableListOf<Long>()
        override suspend fun wait(milliseconds: Long) {
            waited += milliseconds
            if (throwsCancellation) throw CancellationException("cancel delay")
        }
    }

    private fun request(vararg targets: AppLaunchTarget) = WorkspaceLaunchRequest(
        workspaceId = "workspace-id",
        workspaceName = "Workspace",
        targets = targets.toList(),
    )

    private fun target(
        order: Int,
        identity: AppIdentity = AppIdentity("com.example.$order", "Activity$order"),
    ) = AppLaunchTarget(identity, NormalizedBounds.FullCanvas, order)

    private fun success(target: AppLaunchTarget) =
        SingleAppLaunchResult.Success(AppLaunchTargetResult(target))

    private fun failure(order: Int, reason: AppLaunchFailureReason) =
        SingleAppLaunchResult.Failure(AppLaunchFailure(target(order), reason))

    private fun WorkspaceLaunchResult.launchedOrders(): List<Int> = when (this) {
        is WorkspaceLaunchResult.Success -> launchedTargets.map { it.target.order }
        is WorkspaceLaunchResult.PartialSuccess -> launchedTargets.map { it.target.order }
        is WorkspaceLaunchResult.Failure -> emptyList()
    }

    private fun WorkspaceLaunchResult.failedOrders() = failures().map { it.target.order }

    private fun WorkspaceLaunchResult.failures(): List<AppLaunchFailure> = when (this) {
        is WorkspaceLaunchResult.Success -> emptyList()
        is WorkspaceLaunchResult.PartialSuccess -> failedTargets
        is WorkspaceLaunchResult.Failure -> failures
    }
}
