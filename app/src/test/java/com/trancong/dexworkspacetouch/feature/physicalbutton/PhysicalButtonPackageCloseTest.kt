package com.trancong.dexworkspacetouch.feature.physicalbutton

import com.trancong.dexworkspacetouch.workspace.close.*
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhysicalButtonPackageCloseTest {
    @Test fun shortUpAndDown_launchExactWorkspaceWithoutClosing() = runTest {
        val harness = Harness(this)
        for (key in listOf(24, 25)) {
            val press = harness.down(key)
            harness.controller.handle(press)
            advanceTimeBy(799)
            harness.release(press)
            runCurrent()
        }
        assertEquals(listOf("up-id", "down-id"), harness.launched.map { it.id })
        assertEquals(listOf("up-id", "down-id"), harness.repository.requestedIds)
        assertEquals(listOf("DISPATCHED", "DISPATCHED"), harness.results)
        assertEquals(2, harness.launchGateCalls)
        assertTrue(harness.backend.requests.isEmpty())
    }

    @Test fun longUpAndDown_closeExactDistinctPackagesOnceDespiteRepeatsAndUp() = runTest {
        val harness = Harness(this)
        harness.backend.status = PackageCloseStatus.COMPLETE
        for (key in listOf(24, 25)) {
            val press = harness.down(key)
            harness.controller.handle(press)
            advanceTimeBy(799)
            runCurrent()
            assertEquals(if (key == 24) 0 else 1, harness.backend.requests.size)
            advanceTimeBy(1)
            runCurrent()
            repeat(5) {
                harness.controller.handle(press.copy(repeatCount = it % 2, eventTime = testScheduler.currentTime))
            }
            advanceTimeBy(1000)
            harness.release(press)
            harness.release(press)
            runCurrent()
        }
        assertEquals(listOf("up-id", "down-id"), harness.repository.requestedIds)
        assertEquals(listOf(listOf("com.example.alpha", "com.example.shared"),
            listOf("com.example.beta", "com.example.shared")), harness.backend.requests.map { it.packagesRequested })
        assertEquals(listOf(0, 0), harness.backend.requests.map { it.userId })
        assertNotEquals(harness.backend.requests[0].operationId, harness.backend.requests[1].operationId)
        assertEquals(listOf("CLOSE_COMPLETE", "CLOSE_COMPLETE"), harness.results)
        assertNoLaunch(harness)
    }

    @Test fun releaseAtThresholdBeforeTimer_closesOnceWithoutLaunching() = runTest {
        val harness = Harness(this)
        val press = harness.down(25)
        harness.controller.handle(press)
        advanceTimeBy(800)
        harness.release(press)
        runCurrent()
        assertEquals(listOf(listOf("com.example.beta", "com.example.shared")),
            harness.backend.requests.map { it.packagesRequested })
        assertNoLaunch(harness)
    }

    @Test fun repeatAtThresholdBeforeTimer_closesOnceWithoutLaunching() = runTest {
        val harness = Harness(this)
        val press = harness.down(24)
        harness.controller.handle(press)
        advanceTimeBy(800)
        harness.controller.handle(press.copy(repeatCount = 0, eventTime = testScheduler.currentTime))
        runCurrent()
        harness.release(press)
        runCurrent()
        assertEquals(1, harness.backend.requests.size)
        assertNoLaunch(harness)
    }

    @Test fun backendStatuses_remainDistinctWithoutRetryOrLaunchFallback() = runTest {
        val cases = listOf(
            PackageCloseStatus.COMPLETE to "CLOSE_COMPLETE",
            PackageCloseStatus.NO_OP to "CLOSE_NO_OP",
            PackageCloseStatus.PARTIAL to "CLOSE_PARTIAL",
            PackageCloseStatus.BACKEND_UNAVAILABLE to "CLOSE_BACKEND_UNAVAILABLE",
            PackageCloseStatus.UNSUPPORTED to "CLOSE_UNSUPPORTED",
        )
        for ((status, expected) in cases) {
            for (key in listOf(24, 25)) {
                val harness = Harness(this)
                harness.backend.status = status
                assertEquals(expected, harness.dispatcher.dispatch(keyCode = key, gesture = PhysicalButtonGesture.LONG_PRESS).name)
                advanceTimeBy(20_000)
                runCurrent()
                assertEquals(1, harness.backend.requests.size)
                assertNoLaunch(harness)
            }
        }
    }

    @Test fun emptyWorkspace_delegatesEmptySnapshotAndCompletesAsNoOp() = runTest {
        val harness = Harness(this)
        harness.repository.workspaces["up-id"] = workspace("up-id", null)
        assertEquals("CLOSE_NO_OP", harness.dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS).name)
        assertEquals(listOf(emptyList<String>()), harness.backend.requests.map { it.packagesRequested })
        assertNoLaunch(harness)
    }

    @Test fun absentWorkspace_failsClosedWithoutBackendOrLauncher() = runTest {
        for (key in listOf(24, 25)) {
            val harness = Harness(this)
            harness.repository.workspaces.remove(if (key == 24) "up-id" else "down-id")
            assertEquals(PhysicalButtonActionResult.WORKSPACE_UNAVAILABLE,
                harness.dispatcher.dispatch(keyCode = key, gesture = PhysicalButtonGesture.LONG_PRESS))
            assertTrue(harness.backend.requests.isEmpty())
            assertNoLaunch(harness)
        }
    }

    @Test fun deletedWorkspaceDuringPress_neverClosesAnotherWorkspace() = runTest {
        for (key in listOf(24, 25)) {
            val harness = Harness(this)
            val press = harness.down(key)
            harness.controller.handle(press)
            harness.repository.workspaces.remove(if (key == 24) "up-id" else "down-id")
            advanceTimeBy(800)
            runCurrent()
            harness.release(press)
            assertTrue(harness.backend.requests.isEmpty())
            assertTrue(harness.results.isEmpty())
            assertNoLaunch(harness)
        }
    }

    @Test fun configChangedDuringPress_neverClosesOldOrNewWorkspace() = runTest {
        for (key in listOf(24, 25)) {
            val changes: List<(PhysicalButtonPreferences) -> Unit> = listOf(
                { it.setVolumeUpWorkspace("down-id") },
                { it.setVolumeDownWorkspace("up-id") },
                { it.setEnabled(false) },
                { it.clearRemoteBinding() },
                { it.beginBinding() },
            )
            for (change in changes) {
                val harness = Harness(this)
                val press = harness.down(key)
                harness.controller.handle(press)
                change(harness.preferences)
                advanceTimeBy(800)
                runCurrent()
                harness.release(press)
                assertTrue(harness.backend.requests.isEmpty())
                assertTrue(harness.results.isEmpty())
                assertNoLaunch(harness)
            }
        }
    }

    @Test fun invalidatedPress_cannotCloseAfterMappingIsRestored() = runTest {
        val harness = Harness(this)
        val press = harness.down(24)
        harness.controller.handle(press)
        harness.preferences.setVolumeUpWorkspace("down-id")
        harness.controller.invalidatePresses()
        harness.preferences.setVolumeUpWorkspace("up-id")
        advanceTimeBy(800)
        runCurrent()
        harness.release(press)
        assertTrue(harness.backend.requests.isEmpty())
        assertNoLaunch(harness)
    }

    @Test fun configChangedBeforeQueuedDispatch_blocksClose() = runTest {
        val harness = Harness(this)
        val expected = harness.preferences.configuration.value
        harness.preferences.setVolumeDownWorkspace("up-id")
        assertEquals(PhysicalButtonActionResult.BLOCKED,
            harness.dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS, expectedConfiguration = expected))
        assertTrue(harness.repository.requestedIds.isEmpty())
        assertTrue(harness.backend.requests.isEmpty())
        assertNoLaunch(harness)
    }

    @Test fun configOrBindingChangedWhileLoading_blocksClose() = runTest {
        val changes: List<(PhysicalButtonPreferences) -> Unit> = listOf(
            { it.setVolumeUpWorkspace("down-id") },
            { it.setVolumeDownWorkspace("up-id") },
            { it.setEnabled(false) },
            { it.clearRemoteBinding() },
            { it.beginBinding() },
        )
        for (change in changes) {
            val harness = Harness(this)
            harness.repository.beforeRead = { change(harness.preferences) }
            assertEquals(PhysicalButtonActionResult.BLOCKED, harness.dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS))
            assertTrue(harness.backend.requests.isEmpty())
            assertNoLaunch(harness)
        }
    }

    @Test fun wrongWorkspaceReturnedByRepository_failsClosed() = runTest {
        val harness = Harness(this)
        harness.repository.workspaces["up-id"] = workspace("wrong-id", "com.example.wrong")
        assertEquals(PhysicalButtonActionResult.WORKSPACE_UNAVAILABLE,
            harness.dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS))
        assertTrue(harness.backend.requests.isEmpty())
        assertNoLaunch(harness)
    }

    @Test fun requestPackages_remainSnapshotAfterWorkspaceCellsChange() = runTest {
        val harness = Harness(this)
        val cells = harness.repository.workspaces.getValue("up-id").canvas.cells.toMutableList()
        harness.repository.workspaces["up-id"] = harness.repository.workspaces.getValue("up-id").copy(canvas = WorkspaceCanvas(cells))
        harness.backend.beforeClose = { cells.clear() }
        harness.dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS)
        assertEquals(listOf("com.example.alpha", "com.example.shared"), harness.backend.requests.single().packagesRequested)
        assertNoLaunch(harness)
    }

    @Test fun backendException_failsWithoutRetryOrLauncher() = runTest {
        val harness = Harness(this)
        harness.backend.failure = IllegalStateException("backend lost")
        assertEquals(PhysicalButtonActionResult.FAILED, harness.dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS))
        assertEquals(1, harness.backend.requests.size)
        assertNoLaunch(harness)
    }

    @Test fun backendCancellation_propagatesWithoutRetryOrLauncher() = runTest {
        val harness = Harness(this)
        harness.backend.failure = CancellationException("service destroyed")
        try {
            harness.dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS)
            error("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(1, harness.backend.requests.size)
            assertNoLaunch(harness)
        }
    }

    @Test fun dwtPackage_staysProtectedByRealCloseCoordinator() = runTest {
        val harness = Harness(this)
        harness.repository.workspaces["up-id"] = workspace("up-id", "com.example.alpha", DWT_PROTECTED_PACKAGE)
        val dispatcher = harness.dispatcher(realCoordinator())
        assertEquals("CLOSE_UNSUPPORTED", dispatcher.dispatch(gesture = PhysicalButtonGesture.LONG_PRESS).name)
        assertNoLaunch(harness)
    }

    @Test fun emptyWorkspace_realCloseCoordinatorDoesNotTouchTaskBackend() = runTest {
        val harness = Harness(this)
        harness.repository.workspaces["down-id"] = workspace("down-id")
        val dispatcher = harness.dispatcher(realCoordinator())
        assertEquals("CLOSE_NO_OP", dispatcher.dispatch(keyCode = 25, gesture = PhysicalButtonGesture.LONG_PRESS).name)
        assertNoLaunch(harness)
    }

    private fun realCoordinator(): PackageCloseBackend {
        val records = mutableMapOf<String, CloseOperationRecord>()
        val store = object : CloseOperationStore {
            override fun read(operationId: String) = records[operationId]
            override fun pending() = records.values.filter { it.terminal == null }
            override fun write(record: CloseOperationRecord) { records[record.request.operationId] = record }
        }
        val taskBackend = object : CloseTaskBackend {
            override val quiescent = true
            override suspend fun checkAvailable() { error("Task backend must not be called") }
            override suspend fun open(request: PackageCloseRequest, deadline: Long) { error("Must not open") }
            override suspend fun enumerate(): CloseSnapshot = error("Must not enumerate")
            override suspend fun removeVerified(expected: CloseTask): CloseTaskResult = error("Must not remove")
            override fun invalidate() = Unit
        }
        return PackageCloseCoordinator(taskBackend, store, { 0L })
    }

    private fun assertNoLaunch(harness: Harness) {
        assertEquals(0, harness.launchGateCalls)
        assertTrue(harness.launched.isEmpty())
    }

    private class Harness(private val scope: TestScope) {
        val preferences = PhysicalButtonPreferences(object : PhysicalButtonStorage {
            var stored = PhysicalButtonConfiguration(true, "up-id", "down-id", "remote-descriptor")
            override fun read() = stored
            override fun write(configuration: PhysicalButtonConfiguration) { stored = configuration }
        })
        val repository = Repository(mutableMapOf(
            "up-id" to workspace("up-id", "com.example.alpha", "com.example.shared", "com.example.alpha", null),
            "down-id" to workspace("down-id", "com.example.beta", "com.example.shared", "com.example.beta"),
        ))
        val backend = RecordingPackageCloseBackend()
        val launched = mutableListOf<Workspace>()
        var launchGateCalls = 0
        val results = mutableListOf<String>()
        fun dispatcher(closeBackend: PackageCloseBackend = backend) = PhysicalButtonActionDispatcher(
            preferences, repository, closeBackend, { launchGateCalls++; true },
            { launched += it; true },
        )
        val dispatcher = dispatcher()
        val controller = PhysicalButtonGestureController(
            scope, { preferences.configuration.value }, { repository.workspaces.keys },
            { preferences.bindingRequested.value }, { scope.testScheduler.currentTime },
            { gesture, event, snapshot -> scope.launch {
                results += dispatcher.dispatch(snapshot.deviceDescriptor, event.keyCode,
                    snapshot.workspaceForKey(event.keyCode), gesture, snapshot).name
            } },
        )
        fun down(key: Int) = PhysicalButtonKeyEvent(key, 0, 0, 7, scope.testScheduler.currentTime, true, "remote-descriptor")
        fun release(down: PhysicalButtonKeyEvent) = controller.handle(down.copy(action = 1, eventTime = scope.testScheduler.currentTime))
    }

    private class Repository(val workspaces: MutableMap<String, Workspace>) : WorkspaceRepository {
        val requestedIds = mutableListOf<String>()
        var beforeRead: suspend () -> Unit = {}
        override fun observeAll() = flowOf(workspaces.values.toList())
        override suspend fun getById(id: String): Workspace? { requestedIds += id; beforeRead(); return workspaces[id] }
        override suspend fun insert(workspace: Workspace) { workspaces[workspace.id] = workspace }
        override suspend fun update(workspace: Workspace) { workspaces[workspace.id] = workspace }
        override suspend fun deleteById(id: String) { workspaces.remove(id) }
        override suspend fun exists(id: String) = id in workspaces
        override suspend fun count() = workspaces.size
    }

    companion object {
        private fun workspace(id: String, vararg packages: String?) = Workspace(id, id,
            WorkspaceCanvas(packages.mapIndexed { index, pkg -> WorkspaceCell("cell-$index", NormalizedBounds.FullCanvas,
                pkg?.let { AssignedApp(it, "$it.MainActivity", it) }) }), 0, 1, 0, 0)
    }
}

internal class RecordingPackageCloseBackend : PackageCloseBackend {
    val requests = mutableListOf<PackageCloseRequest>()
    var status = PackageCloseStatus.NO_OP
    var failure: Exception? = null
    var beforeClose: suspend () -> Unit = {}
    override suspend fun closePackages(request: PackageCloseRequest): PackageCloseReport {
        requests += request
        beforeClose()
        failure?.let { throw it }
        return PackageCloseReport(request, status, null, emptyList(), emptyList(), emptyMap(),
            null, emptyList(), 0L, 1L, CloseLimits())
    }
}
