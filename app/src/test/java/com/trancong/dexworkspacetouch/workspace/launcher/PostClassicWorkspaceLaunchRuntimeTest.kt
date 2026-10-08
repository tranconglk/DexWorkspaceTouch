package com.trancong.dexworkspacetouch.workspace.launcher

import com.trancong.dexworkspacetouch.feature.car.overlay.*
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.platform.launch.shizuku.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import com.trancong.dexworkspacetouch.workspace.launcher.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PostClassicWorkspaceLaunchRuntimeTest {
    @Test fun partialAndFailedLaunchPrepareCurrentManualDisplayWithoutAutoSuccess() = runTest {
        val failure=AppLaunchFailure(target,AppLaunchFailureReason.UNKNOWN)
        var manual:DisplayWorkAreaSnapshot?=snapshot
        var completions=0
        for(after in listOf(snapshot.copy(displayId=2),null)) {
            val runtime=PostClassicWorkspaceLaunchRuntime(Runtime(WorkspaceLaunchResult.PartialSuccess(
                listOf(AppLaunchTargetResult(target)),listOf(failure))),{ after },{}, { _, _ -> completions++ },
                onPrepared={ manual=it })
            runtime.launch(request)
            assertEquals(after,manual)
        }
        assertEquals(0,completions)
    }
    @Test fun hostWindowChangesDoNotDiscardSuccessOnSameRepairGeometry() = runTest {
        var current=snapshot
        var completed=0
        val runtime=PostClassicWorkspaceLaunchRuntime(Runtime(success) {
            current=snapshot.copy(hostWindowBounds=DiagnosticPixelBounds(100,100,800,800),hostWindowMode=HostWindowMode.WINDOWED)
        },{ current },{}, { _, _ -> completed++ })
        runtime.launch(request)
        assertEquals(1,completed)
    }
    @Test fun libraryViewModelAndCarShortcutPlatformUseSamePostSuccessBoundary() = runTest {
        val canvas=com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas(listOf(
            com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell("cell",NormalizedBounds.FullCanvas,
                com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp("com.example.app","com.example.app.Main","App"))))
        val workspace=com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace("one","Workspace",canvas,1,1,1,1)
        val repository=object:com.trancong.dexworkspacetouch.workspace.persistence.repository.WorkspaceRepository {
            override fun observeAll()=kotlinx.coroutines.flow.flowOf(listOf(workspace))
            override suspend fun getById(id:String)=workspace.takeIf { it.id==id }
            override suspend fun insert(workspace:com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace)=Unit
            override suspend fun update(workspace:com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace)=Unit
            override suspend fun deleteById(id:String)=Unit
            override suspend fun exists(id:String)=id==workspace.id
            override suspend fun count()=1
        }
        val factory=WorkspaceLaunchRequestFactory(com.trancong.dexworkspacetouch.workspace.apppicker.model.ListInstalledAppCatalog(listOf(
            com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp("com.example.app","com.example.app.Main","App",true))))
        var assessments=0; var repairs=0
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),
            { assessments++; ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.REPAIR_AVAILABLE) },
            { error("No manual action") },autoRepairEnabled=MutableStateFlow(true),
            automaticRepair={ _, _ -> repairs++; CarDockRepairState("Repaired",true) })
        val runtime=PostClassicWorkspaceLaunchRuntime(Runtime(success),{ snapshot },session::classicLaunchStarted,
            { current, _ -> session.classicLaunchCompleted(current) })
        val vm=WorkspaceLaunchViewModel({ LaunchReadiness.Ready(request) },this)
        vm.launchWorkspace(com.trancong.dexworkspacetouch.workspace.library.model.WorkspaceLibraryItem("one","Workspace",canvas,1),runtime,Any())
        advanceUntilIdle()
        assertTrue((vm.state as WorkspaceLaunchUiState.Completed).result is WorkspaceLaunchResult.Success)
        val car=com.trancong.dexworkspacetouch.feature.car.platform.RepositoryCarWorkspaceLaunchPlatform(repository,factory,runtime)
        assertSame(com.trancong.dexworkspacetouch.feature.car.platform.CarWorkspaceLaunchResult.Success,car.launch("one"))
        advanceUntilIdle()
        assertEquals(2,assessments); assertEquals(2,repairs)
        session.dispose()
    }
    @Test fun sharedLaunchBoundaryRepairsAfterSuccessWithoutDockOrUiLifetime() = runTest {
        for (entry in listOf("Library", "Car", "Shortcut")) {
            var assessments = 0
            var repairs = 0
            val session = WorkspaceRepairSession(this, MutableStateFlow(false),
                { assessments++; ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.REPAIR_AVAILABLE) },
                { error("No manual click") }, autoRepairEnabled=MutableStateFlow(true),
                automaticRepair={ _, _ -> repairs++; CarDockRepairState("Repaired",true) })
            val runtime = PostClassicWorkspaceLaunchRuntime(Runtime(success), { snapshot },
                session::classicLaunchStarted, { current, observed ->
                    assertEquals(snapshot, observed); session.classicLaunchCompleted(current)
                })
            assertSame(success, runtime.launch(request.copy(workspaceName=entry)))
            advanceUntilIdle()
            assertEquals(1, assessments); assertEquals(1, repairs)
            advanceTimeBy(30000); runCurrent()
            assertEquals(1, repairs)
            session.dispose()
        }
    }

    @Test fun partialFailureAndCancellationNeverTriggerPostClassicRepair() = runTest {
        val failure = AppLaunchFailure(target, AppLaunchFailureReason.UNKNOWN)
        for (result in listOf(WorkspaceLaunchResult.Failure(listOf(failure)),
            WorkspaceLaunchResult.PartialSuccess(listOf(AppLaunchTargetResult(target)),listOf(failure)))) {
            var starts=0; var completions=0
            val runtime=PostClassicWorkspaceLaunchRuntime(Runtime(result),{ snapshot },{ starts++ },{ _, _ -> completions++ })
            assertSame(result,runtime.launch(request))
            assertEquals(1,starts); assertEquals(0,completions)
        }
        var completions=0
        val delegate=object:WorkspaceLaunchRuntime {
            override fun checkEnvironment()=LaunchEnvironmentCheck.Ready
            override suspend fun launch(request:WorkspaceLaunchRequest):WorkspaceLaunchResult=throw CancellationException()
        }
        try { PostClassicWorkspaceLaunchRuntime(delegate,{ snapshot },{}, { _, _ -> completions++ }).launch(request)
            fail("Cancellation must propagate")
        } catch (_:CancellationException) { }
        assertEquals(0,completions)
    }

    @Test fun displayChangedOrUnavailableSkipsAndCallbackFailureCannotChangeClassicSuccess() = runTest {
        for (after in listOf(null,snapshot.copy(displayId=2),snapshot.copy(density=2f))) {
            var current:DisplayWorkAreaSnapshot?=snapshot
            var completed=0
            val runtime=PostClassicWorkspaceLaunchRuntime(Runtime(success) { current=after },{ current },{},
                { _, _ -> completed++ })
            assertSame(success,runtime.launch(request)); assertEquals(0,completed)
        }
        val runtime=PostClassicWorkspaceLaunchRuntime(Runtime(success),{ snapshot },{ error("Optional start failed") },
            { _, _ -> error("Optional repair failed") })
        assertSame(success,runtime.launch(request))
    }

    private class Runtime(private val result:WorkspaceLaunchResult, private val duringLaunch:()->Unit={}) : WorkspaceLaunchRuntime {
        override fun checkEnvironment()=LaunchEnvironmentCheck.Ready
        override suspend fun launch(request:WorkspaceLaunchRequest):WorkspaceLaunchResult { duringLaunch(); return result }
    }
    private val snapshot=DisplayWorkAreaSnapshot(1,DisplayWorkArea(1920,1080),
        DiagnosticPixelBounds(0,0,1920,1080),DiagnosticPixelBounds(0,0,1920,1080),1f,HostWindowMode.MAXIMIZED)
    private val target=AppLaunchTarget("cell",AppIdentity("com.example.app","com.example.app.Main"),NormalizedBounds.FullCanvas,0)
    private val request=WorkspaceLaunchRequest("one","Workspace",listOf(target))
    private val success=WorkspaceLaunchResult.Success(listOf(AppLaunchTargetResult(target)))
}
