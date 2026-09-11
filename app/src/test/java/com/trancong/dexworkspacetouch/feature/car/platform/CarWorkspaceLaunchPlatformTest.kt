package com.trancong.dexworkspacetouch.feature.car.platform

import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.ListInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailure
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchFailureReason
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTargetResult
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchResult
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.LaunchEnvironmentCheck
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.LaunchEnvironmentFailure
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.WorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CarWorkspaceLaunchPlatformTest {
    @Test
    fun validWorkspace_launchesExactWorkspaceWithoutMutation() = runBlocking {
        val original = validWorkspace
        val runtime = FakeRuntime()
        val repository = FakeRepository(original)

        val result = platform(repository, runtime = runtime).launch(original.id)

        assertSame(CarWorkspaceLaunchResult.Success, result)
        assertEquals(listOf(original.id), repository.requestedIds)
        assertEquals(original, validWorkspace)
        assertEquals(original.id, runtime.requests.single().workspaceId)
        assertEquals(original.canvas.cells.map { it.bounds }, runtime.requests.single().targets.map { it.bounds })
    }

    @Test
    fun missingWorkspace_returnsWorkspaceUnavailable() = runBlocking {
        val result = platform(FakeRepository(null)).launch("missing-id")

        assertSame(CarWorkspaceLaunchResult.WorkspaceUnavailable, result)
    }

    @Test
    fun repositoryFailure_returnsExecutionFailed() = runBlocking {
        val result = platform(FakeRepository(failure = IllegalStateException("Database failed.")))
            .launch("workspace-id")

        assertEquals(CarWorkspaceLaunchResult.ExecutionFailed("Database failed."), result)
    }

    @Test
    fun emptyWorkspace_returnsWorkspaceUnavailableWithoutRuntimeCall() = runBlocking {
        val runtime = FakeRuntime()
        val result = platform(
            FakeRepository(validWorkspace.copy(canvas = WorkspaceCanvas(emptyList()))),
            runtime = runtime,
        ).launch(validWorkspace.id)

        assertSame(CarWorkspaceLaunchResult.WorkspaceUnavailable, result)
        assertTrue(runtime.requests.isEmpty())
    }

    @Test
    fun missingComponent_returnsWorkspaceUnavailableWithoutRuntimeCall() = runBlocking {
        val runtime = FakeRuntime()
        val result = platform(
            FakeRepository(validWorkspace),
            installedApps = emptyList(),
            runtime = runtime,
        ).launch(validWorkspace.id)

        assertSame(CarWorkspaceLaunchResult.WorkspaceUnavailable, result)
        assertTrue(runtime.requests.isEmpty())
    }

    @Test
    fun unavailableEnvironment_returnsExecutionFailedWithoutLaunch() = runBlocking {
        val runtime = FakeRuntime(
            environment = LaunchEnvironmentCheck.Unavailable(
                LaunchEnvironmentFailure.HOST_NOT_EXTERNAL,
            ),
        )

        val result = platform(FakeRepository(validWorkspace), runtime = runtime)
            .launch(validWorkspace.id)

        assertTrue(result is CarWorkspaceLaunchResult.ExecutionFailed)
        assertTrue(runtime.requests.isEmpty())
    }

    @Test
    fun partialWorkspaceLaunch_returnsExecutionFailed() = runBlocking {
        val request = readyRequest()
        val result = platform(
            FakeRepository(validWorkspace),
            runtime = FakeRuntime(
                result = WorkspaceLaunchResult.PartialSuccess(
                    launchedTargets = listOf(AppLaunchTargetResult(request.targets.first())),
                    failedTargets = listOf(
                        AppLaunchFailure(
                            request.targets.first(),
                            AppLaunchFailureReason.LAUNCH_REJECTED,
                        ),
                    ),
                ),
            ),
        ).launch(validWorkspace.id)

        assertTrue(result is CarWorkspaceLaunchResult.ExecutionFailed)
    }

    @Test
    fun unavailableComponentsFromLaunchResult_returnsWorkspaceUnavailable() = runBlocking {
        val request = readyRequest()
        val result = platform(
            FakeRepository(validWorkspace),
            runtime = FakeRuntime(
                result = WorkspaceLaunchResult.Failure(
                    listOf(
                        AppLaunchFailure(
                            request.targets.first(),
                            AppLaunchFailureReason.ACTIVITY_NOT_FOUND,
                        ),
                    ),
                ),
            ),
        ).launch(validWorkspace.id)

        assertSame(CarWorkspaceLaunchResult.WorkspaceUnavailable, result)
    }

    @Test
    fun cancellationFromRepository_propagates() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                platform(FakeRepository(failure = CancellationException("cancelled")))
                    .launch(validWorkspace.id)
            }
        }
    }

    @Test
    fun cancellationFromRuntime_propagates() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                platform(
                    FakeRepository(validWorkspace),
                    runtime = FakeRuntime(failure = CancellationException("cancelled")),
                ).launch(validWorkspace.id)
            }
        }
    }

    private fun platform(
        repository: WorkspaceRepository,
        installedApps: List<InstalledApp> = listOf(installedApp),
        runtime: WorkspaceLaunchRuntime = FakeRuntime(),
    ) = RepositoryCarWorkspaceLaunchPlatform(
        repository = repository,
        requestFactory = WorkspaceLaunchRequestFactory(ListInstalledAppCatalog(installedApps)),
        runtime = runtime,
    )

    private fun readyRequest(): WorkspaceLaunchRequest {
        val runtime = FakeRuntime()
        runBlocking { platform(FakeRepository(validWorkspace), runtime = runtime).launch(validWorkspace.id) }
        return runtime.requests.single()
    }

    private class FakeRepository(
        private val workspace: Workspace? = null,
        private val failure: Exception? = null,
    ) : WorkspaceRepository {
        val requestedIds = mutableListOf<String>()

        override fun observeAll(): Flow<List<Workspace>> = flowOf(listOfNotNull(workspace))
        override suspend fun getById(id: String): Workspace? {
            requestedIds += id
            failure?.let { throw it }
            return workspace?.takeIf { it.id == id }
        }
        override suspend fun insert(workspace: Workspace) = Unit
        override suspend fun update(workspace: Workspace) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun exists(id: String): Boolean = workspace?.id == id
        override suspend fun count(): Int = if (workspace == null) 0 else 1
    }

    private class FakeRuntime(
        private val environment: LaunchEnvironmentCheck = LaunchEnvironmentCheck.Ready,
        private val result: WorkspaceLaunchResult? = null,
        private val failure: Exception? = null,
    ) : WorkspaceLaunchRuntime {
        val requests = mutableListOf<WorkspaceLaunchRequest>()

        override fun checkEnvironment(): LaunchEnvironmentCheck = environment

        override suspend fun launch(request: WorkspaceLaunchRequest): WorkspaceLaunchResult {
            requests += request
            failure?.let { throw it }
            return result ?: WorkspaceLaunchResult.Success(
                request.targets.map(::AppLaunchTargetResult),
            )
        }
    }

    private companion object {
        val installedApp = InstalledApp(
            packageName = "com.example.maps",
            activityName = "com.example.maps.MainActivity",
            label = "Maps",
            launchable = true,
        )
        val assignedApp = AssignedApp(
            packageName = installedApp.packageName,
            activityName = installedApp.activityName,
            label = installedApp.label,
        )
        val validWorkspace = Workspace(
            id = "workspace-id",
            name = "Drive",
            canvas = WorkspaceCanvas(
                listOf(
                    WorkspaceCell("cell", NormalizedBounds.FullCanvas, assignedApp),
                ),
            ),
            modifiedSequence = 1,
            schemaVersion = 1,
            createdAtEpochMillis = 100,
            updatedAtEpochMillis = 100,
        )
    }
}
