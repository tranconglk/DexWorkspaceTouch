package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.platform.launch.shizuku.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRepairSessionTest {
    @Test fun classicSuccessStabilizesThenAssessesOnceWithoutManualMutation() = runTest {
        var reads=0; var repairs=0
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ reads++; available },{ repairs++; CarDockRepairState("Repaired") })
        session.classicLaunchStarted("one")
        session.classicLaunchCompleted(request("one"))
        runCurrent()
        assertEquals("Checking layout\u2026",session.state.value.label)
        advanceTimeBy(999); runCurrent()
        assertEquals(0,reads)
        advanceTimeBy(1); runCurrent()
        assertEquals("Repair available",session.state.value.label)
        assertEquals("one",session.state.value.workspaceId)
        assertEquals(1,reads); assertEquals(0,repairs)
        advanceTimeBy(10000); runCurrent()
        assertEquals(1,reads)
        session.dispose()
    }
    @Test fun correctPartialAndUnavailableNeverShowRepairAvailable() = runTest {
        for(status in listOf(WorkspaceAssessmentStatus.LAYOUT_CORRECT,WorkspaceAssessmentStatus.PARTIAL_OR_UNRESOLVED,WorkspaceAssessmentStatus.UNAVAILABLE)) {
            val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ ExistingWorkspaceAssessmentReport(status) },{ error("No manual action") })
            session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"))
            advanceUntilIdle()
            assertEquals(status,session.state.value.assessment!!.status)
            assertNotEquals("Repair available",session.state.value.label)
            assertTrue(session.state.value.enabled)
            session.dispose()
        }
    }
    @Test fun workspaceChangesDiscardInFlightResultEvenIfTransportFinishesLate() = runTest {
        val late=CompletableDeferred<ExistingWorkspaceAssessmentReport>()
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ withContext(NonCancellable) { late.await() } },{ error("No click") })
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"))
        advanceTimeBy(1000);runCurrent()
        session.selectWorkspace("two")
        late.complete(available);advanceUntilIdle()
        assertEquals("two",session.state.value.workspaceId)
        assertEquals("Repair",session.state.value.label)
        assertNull(session.state.value.assessment)
        session.dispose()
    }
    @Test fun secondClassicLaunchSameWorkspaceInvalidatesFirstGeneration() = runTest {
        val late=CompletableDeferred<ExistingWorkspaceAssessmentReport>();var reads=0
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ reads++; if(reads==1) withContext(NonCancellable) { late.await() }
            else ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.LAYOUT_CORRECT) },{ error("No click") })
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"))
        advanceTimeBy(1000);runCurrent()
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"))
        advanceTimeBy(1000);runCurrent()
        late.complete(available);advanceUntilIdle()
        assertEquals(2,reads)
        assertEquals(WorkspaceAssessmentStatus.LAYOUT_CORRECT,session.state.value.assessment!!.status)
        session.dispose()
    }
    @Test fun conflictCancelsCheckingAndClearsAlreadyPublishedSuggestion() = runTest {
        var reads=0
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ reads++;available },{ error("No click") })
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"))
        runCurrent();session.controlActionStarted();advanceUntilIdle()
        assertEquals(0,reads);assertEquals("Repair",session.state.value.label)
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"));advanceUntilIdle()
        assertEquals("Repair available",session.state.value.label)
        session.controlActionStarted()
        assertNull(session.state.value.assessment);assertEquals("Repair",session.state.value.label)
        session.dispose()
    }
    @Test fun manualClickCancelsAssessmentAndDelegatesExactlyOnce() = runTest {
        var reads=0;val repairs=mutableListOf<String>()
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ reads++;available },{ repairs+=it;delay(10);CarDockRepairState("\u2713 Repaired",true) })
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"));runCurrent()
        session.repair();session.repair();runCurrent()
        assertEquals("Repairing\u2026",session.state.value.label)
        advanceUntilIdle()
        assertEquals(listOf("one"),repairs);assertEquals(0,reads)
        assertEquals("\u2713 Repaired",session.state.value.label)
        session.dispose()
    }
    @Test fun deadlineBoundsStabilizationAdmissionAndHungAssessment() = runTest {
        for(busy in listOf(false,true)) {
            val session=WorkspaceRepairSession(this,MutableStateFlow(busy),{ awaitCancellation() },{ error("No click") })
            session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"))
            advanceTimeBy(5000);runCurrent()
            assertEquals(WorkspaceAssessmentStatus.UNAVAILABLE,session.state.value.assessment!!.status)
            assertNotEquals("Repair available",session.state.value.label)
            session.dispose()
        }
    }
    @Test fun classicReservationIsReleasedBeforeStabilizationAndRead() = runTest {
        val busy=MutableStateFlow(true);var reads=0
        val session=WorkspaceRepairSession(this,busy,{ reads++;available },{ error("No click") })
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"));runCurrent()
        advanceTimeBy(1000);runCurrent();assertEquals(0,reads)
        busy.value=false;runCurrent();advanceTimeBy(1000);runCurrent()
        assertEquals(1,reads);session.dispose()
    }
    @Test fun contextInvalidationDiscardsReadAndDisposeLeavesNoWatcher() = runTest {
        var reads=0
        val session=WorkspaceRepairSession(this,MutableStateFlow(false),{ reads++;available },{ error("No click") })
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"));runCurrent()
        session.invalidateSuggestion();advanceUntilIdle()
        assertEquals(0,reads)
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"));session.dispose();advanceUntilIdle()
        assertEquals(0,reads)
    }
    @Test fun conflictAfterReadReservationEndsDiscardsResultBeforePublication() = runTest {
        lateinit var session: WorkspaceRepairSession
        session=WorkspaceRepairSession(this,MutableStateFlow(false),{
            session.assessmentReservationChanged(true)
            session.controlActionStarted()
            assertEquals("Checking layout\u2026",session.state.value.label)
            session.assessmentReservationChanged(false)
            session.controlActionStarted()
            available
        },{ error("No click") })
        session.classicLaunchStarted("one");session.classicLaunchCompleted(request("one"));advanceUntilIdle()
        assertEquals("Repair",session.state.value.label)
        assertNull(session.state.value.assessment)
        session.dispose()
    }
    private val available=ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.REPAIR_AVAILABLE)
    private fun request(id:String)=WorkspaceLaunchRequest(id,"Workspace",listOf(AppLaunchTarget("cell",
        AppIdentity("com.example.app","com.example.app.Main"),NormalizedBounds.FullCanvas,0)))
}
