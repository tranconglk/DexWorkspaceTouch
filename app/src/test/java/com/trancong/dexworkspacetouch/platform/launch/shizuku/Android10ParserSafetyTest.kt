package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import org.junit.Assert.*
import org.junit.Test

class Android10ParserSafetyTest {
    @Test fun reviewProbeIndentedDisplayCannotInheritExternalDisplay() {
        val text = base().replace("Display #2", "Display #2\n  Display #0")
        assertUnsafe(text)
        assertEquals(0, WorkspaceTaskCorrelation.parse(text).single().displayId)
    }

    @Test fun malformedDisplayInvalidatesInheritedContext() {
        val text = base().replace("Display #2", "Display #2\n  Display #INVALID")
        assertUnsafe(text)
        assertEquals(-1, WorkspaceTaskCorrelation.parse(text).single().displayId)
    }

    @Test fun displayBoundaryClearsPreambleAndStack() {
        val text = PREFIX + PREAMBLE + "  Display #3\n" + RECORD + ACTIVITY + VISIBLE
        assertUnsafe(text)
        val task = WorkspaceTaskCorrelation.parse(text).single()
        assertEquals(3, task.displayId)
        assertNull(task.bounds)
        assertFalse(task.freeform)
    }

    @Test fun reviewProbeConflictingBoundsCannotUseLastValue() = assertUnsafe(
        base().replace(PREAMBLE, PREAMBLE + "    mBounds=Rect(100, 100 - 800, 800)\n"))

    @Test fun reviewProbeMalformedTaskBoundaryCannotReusePreamble() = assertUnsafe(
        base().replace(RECORD, "    Task id #INVALID\n" + RECORD))

    @Test fun malformedTaskRecordBoundaryCannotReusePreamble() = assertUnsafe(
        base().replace(RECORD, "    * TaskRecord{bad #INVALID U=0 StackId=5}\n" + RECORD))

    @Test fun malformedBoundsCannotRecoverFromLaterValidBounds() = assertUnsafe(
        base().replace(PREAMBLE, "    Task id #77\n    mBounds=INVALID\n    mBounds=Rect(8, 8 - 958, 1020)\n"))

    @Test fun malformedBoundsAfterValidBoundsInvalidatePreamble() = assertUnsafe(
        base().replace(PREAMBLE, PREAMBLE + "    mBounds=Rect(8, 8 - 1, 1)\n"))

    @Test fun boundsWithTrailingGarbageAreNotEvidence() = assertUnsafe(
        base().replace("mBounds=Rect(8, 8 - 958, 1020)", "mBounds=Rect(8, 8 - 958, 1020) garbage"))

    @Test fun missingPreambleCannotBorrowPreviousTaskBounds() = assertUnsafe(
        base().replace(COMPONENT, OTHER_COMPONENT) + base().substringAfter(PREFIX).replace(PREAMBLE, "").replace("#77", "#78")
            .replace("t77", "t78").replace("task77", "task78").replace("act77", "act78"))

    @Test fun mismatchedPreambleIdIsUnresolved() = assertUnsafe(base().replace("Task id #77", "Task id #999"))

    @Test fun stackBoundaryClearsPendingBounds() {
        val text = PREFIX + PREAMBLE + "  Stack #6: type=standard mode=freeform\n" +
            RECORD.replace("StackId=5", "StackId=6") + ACTIVITY + VISIBLE
        assertUnsafe(text)
        assertNull(WorkspaceTaskCorrelation.parse(text).single().bounds)
    }

    @Test fun duplicateIdenticalPreambleBoundsRemainUnambiguous() {
        val text = base().replace(PREAMBLE, PREAMBLE + "    mBounds=Rect(8, 8 - 958, 1020)\n")
        assertEquals(PixelBounds(8, 8, 958, 1020), selected(text)!!.task.bounds)
    }

    @Test fun preambleCannotSurviveLeavingItsIndentation() = assertUnsafe(
        base().replace(RECORD, "  unrelated stack section\n" + RECORD))

    @Test fun reviewProbeMalformedOtherActivityCannotUpgradeVisibility() = assertUnsafe(
        base().replace(VISIBLE, HIDDEN + "      * Hist #1: ActivityRecord{other u0 com.example.other/.Main tINVALID}\n" + VISIBLE))

    @Test fun reviewProbeConflictingVisibilityIsUnresolved() = assertUnsafe(base() + HIDDEN)

    @Test fun malformedActivityBetweenTargetAndVisibilityIsUnresolved() = assertUnsafe(
        base().replace(VISIBLE, "      * Hist #1: ActivityRecord{INVALID}\n" + VISIBLE))

    @Test fun unrelatedVisibleActivityCannotUpgradeHiddenTarget() = assertUnsafe(
        base().replace(VISIBLE, HIDDEN) + OTHER_ACTIVITY + VISIBLE)

    @Test fun missingActivityVisibilityIsUnresolved() = assertUnsafe(base().replace(VISIBLE, ""))

    @Test fun visibilityOutsideActivityIndentationCannotAuthorizeTarget() = assertUnsafe(
        base().replace(VISIBLE, "    keysPaused=false inHistory=true visible=true\n"))

    @Test fun matchingVisibleActivityWithUnrelatedRecordRemainsUnique() {
        val text = base() + OTHER_ACTIVITY + VISIBLE
        val candidate = selected(text)!!
        assertTrue(candidate.task.visible)
        assertEquals(setOf(COMPONENT, "com.example.other/com.example.other.Main"), candidate.activities)
    }

    @Test fun reviewProbeStackIdMismatchCannotBorrowFreeformMode() {
        val text = base().replace("StackId=5", "StackId=6")
        assertUnsafe(text)
        assertFalse(WorkspaceTaskCorrelation.parse(text).single().freeform)
    }

    @Test fun missingStackRelationshipIsUnresolved() = assertUnsafe(base().replace(" StackId=5", ""))
    @Test fun malformedStackRelationshipIsUnresolved() = assertUnsafe(base().replace("StackId=5", "StackId=INVALID"))
    @Test fun malformedStackBoundaryCannotBorrowPreviousMode() = assertUnsafe(
        base().replace(PREAMBLE, "  Stack #INVALID: type=standard mode=freeform\n" + PREAMBLE))
    @Test fun fullscreenStackIsNeverEligible() = assertUnsafe(base().replace("mode=freeform", "mode=fullscreen"))

    @Test fun reviewProbeDuplicateActivityIdentityIsUnresolved() = assertUnsafe(base() + ACTIVITY + VISIBLE)
    @Test fun twoMatchingComponentActivityRecordsAreUnresolved() = assertUnsafe(
        base() + ACTIVITY.replace("act77", "second77") + VISIBLE)
    @Test fun sameActivityTokenWithDifferentComponentIsUnresolved() = assertUnsafe(
        base() + OTHER_ACTIVITY.replace("other77", "act77") + VISIBLE)
    @Test fun conflictingActivityUserIsUnresolved() = assertUnsafe(
        base() + OTHER_ACTIVITY.replace(" u0 ", " u10 ") + VISIBLE)
    @Test fun conflictingActivityTaskOwnerIsUnresolved() = assertUnsafe(
        base() + OTHER_ACTIVITY.replace(" t77}", " t99}") + VISIBLE)
    @Test fun sameActivityTokenInAnotherTaskIsUnresolved() = assertUnsafe(
        base() + (PREAMBLE + RECORD + ACTIVITY + VISIBLE).replace("#77", "#99")
            .replace("task77", "task99").replace(" t77}", " t99}").replace(COMPONENT, OTHER_COMPONENT))

    @Test fun duplicateTaskRecordsRemainUnresolved() = assertUnsafe(base() + PREAMBLE + RECORD + ACTIVITY + VISIBLE)
    @Test fun validLegacyEvidenceStillMatchesExactIdentity() {
        val candidate = selected(base())!!
        assertEquals("task77:act77:0:$COMPONENT", candidate.identity)
        assertTrue(candidate.task.visible && candidate.task.freeform)
        assertEquals(2, candidate.task.displayId)
        assertEquals(0, candidate.task.userId)
    }

    @Test fun modernInlineModeAndLaunchIdentifierArePreserved() {
        val text = "Display #2\n  * Task{modern #77 U=0 visible=true mode=freeform}\n" +
            "    mBounds=Rect(8, 8 - 958, 1020)\n" + ACTIVITY.replace("cmp=", "id=launch77 cmp=")
        val candidate = selected(text)!!
        assertEquals("launch77", candidate.task.launchIdentifier)
        assertEquals("modern:act77:0:$COMPONENT", candidate.identity)
        assertTrue(candidate.task.visible && candidate.task.freeform)
    }

    @Test fun runningActivitiesCannotSupplyMissingTargetVisibility() = assertUnsafe(
        base().replace(VISIBLE, "    Running activities (most recent first):\n" + ACTIVITY + VISIBLE))

    @Test fun indentedSupervisorBoundaryClosesObservation() {
        val text = base() + "  ActivityTaskSupervisor state:\n" + base()
        assertEquals(1, WorkspaceTaskCorrelation.parse(text).size)
        assertNotNull(selected(text))
    }

    private fun selected(text: String) = ExistingTaskCorrelation.select(ExistingTaskCorrelation.parse(text), COMPONENT, 2)

    private fun assertUnsafe(text: String) {
        val commands = mutableListOf<List<String>>()
        val shell = WorkspaceCommandShell { command ->
            commands += command
            if (command.first() == "am") WorkspaceCommandResult(1, "Error: fake shell refuses mutation")
            else {
                assertEquals(WorkspaceTaskCorrelation.DUMP_COMMAND, command)
                WorkspaceCommandResult(0, text)
            }
        }
        val request = WorkspaceLaunchRequest("safety", "Safety", listOf(AppLaunchTarget("left",
            AppIdentity("com.android.chrome", "com.google.android.apps.chrome.Main"),
            NormalizedBounds(0f, 0f, 1f / 3f, 1f), 0)))
        val snapshot = DisplayWorkAreaSnapshot(2, DisplayWorkArea(1920, 1080, insetBottomPx = 52),
            DiagnosticPixelBounds(0, 0, 1920, 1080), DiagnosticPixelBounds(0, 0, 1920, 1028), 1f, HostWindowMode.MAXIMIZED)
        val report = ExistingWorkspaceRepair(EmbeddedProductRunGate(), CarWorkflowExecutionArbiter(), shell,
            pause = {}, pollAttempts = 3).run(request, snapshot)
        assertFalse(report.complete)
        assertTrue("Unsafe resize command: $commands", commands.none { it.first() == "am" })
        assertNull("Unsafe selectable candidate", selected(text))
    }

    private fun base() = PREFIX + PREAMBLE + RECORD + ACTIVITY + VISIBLE

    private companion object {
        const val COMPONENT = "com.android.chrome/com.google.android.apps.chrome.Main"
        const val OTHER_COMPONENT = "com.example.other/com.example.other.Main"
        const val PREFIX = "Display #2\n  Stack #5: type=standard mode=freeform\n"
        const val PREAMBLE = "    Task id #77\n    mBounds=Rect(8, 8 - 958, 1020)\n"
        const val RECORD = "    * TaskRecord{task77 #77 A=com.android.chrome U=0 StackId=5 sz=1}\n"
        const val ACTIVITY = "      * Hist #0: ActivityRecord{act77 u0 $COMPONENT t77}\n          Intent { cmp=$COMPONENT }\n"
        const val OTHER_ACTIVITY = "      * Hist #1: ActivityRecord{other77 u0 $OTHER_COMPONENT t77}\n          Intent { cmp=$OTHER_COMPONENT }\n"
        const val VISIBLE = "          keysPaused=false inHistory=true visible=true sleeping=false\n"
        const val HIDDEN = "          keysPaused=false inHistory=true visible=false sleeping=false\n"
    }
}
