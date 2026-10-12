package com.trancong.dexworkspacetouch.feature.physicalbutton

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class PhysicalButtonLongPressTest {
    @Test fun firstDown_tracksWithoutDispatchingWorkspace() {
        val handler = PhysicalButtonKeyHandler()
        val configuration = PhysicalButtonConfiguration(true, "up-id", "down-id", "remote-descriptor")
        for (keyCode in listOf(24, 25)) {
            val event = PhysicalButtonKeyEvent(keyCode, 0, 0, 7, 100L, true, "remote-descriptor")
            val decision = handler.handle(event, configuration, setOf("up-id", "down-id"))
            assertEquals("TRACK_PRESS", decision.name)
        }
    }

    @Test fun shortUp_emitsExactlyOneShortPress() = runTest { verifyShortPress(24) }
    @Test fun shortDown_emitsExactlyOneShortPress() = runTest { verifyShortPress(25) }
    @Test fun longUp_withoutRepeats_emitsExactlyOneLongPress() = runTest { verifyLongPress(24) }
    @Test fun longDown_withoutRepeats_emitsExactlyOneLongPress() = runTest { verifyLongPress(25) }

    private fun TestScope.verifyShortPress(keyCode: Int) {
        val harness = Harness(this)
        val down = harness.down(keyCode)
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, harness.handle(down))
        assertTrue(harness.actions.isEmpty())
        advanceTimeBy(799)
        assertTrue(harness.actions.isEmpty())
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, harness.release(down))
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(listOf(PhysicalButtonGesture.SHORT_PRESS to workspaceId(keyCode)), harness.actions)
    }

    private fun TestScope.verifyLongPress(keyCode: Int) {
        val harness = Harness(this)
        val down = harness.down(keyCode)
        assertEquals(PhysicalButtonKeyDecision.TRACK_PRESS, harness.handle(down))
        advanceTimeBy(799)
        runCurrent()
        assertTrue(harness.actions.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(PhysicalButtonGesture.LONG_PRESS to workspaceId(keyCode)), harness.actions)
        advanceTimeBy(1200)
        assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(down))
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, harness.release(down))
        assertEquals(listOf(PhysicalButtonGesture.LONG_PRESS to workspaceId(keyCode)), harness.actions)
    }

    @Test fun repeatsIncludingZeroRepeatCount_neverDuplicateEitherGesture() = runTest {
        for (keyCode in listOf(24, 25)) {
            val harness = Harness(this)
            val down = harness.down(keyCode)
            harness.handle(down)
            repeat(5) {
                advanceTimeBy(100)
                assertEquals(PhysicalButtonKeyDecision.CONSUME,
                    harness.handle(down.copy(repeatCount = it % 2, eventTime = testScheduler.currentTime)))
            }
            assertTrue(harness.actions.isEmpty())
            advanceTimeBy(300)
            runCurrent()
            repeat(5) {
                assertEquals(PhysicalButtonKeyDecision.CONSUME,
                    harness.handle(down.copy(repeatCount = it, eventTime = testScheduler.currentTime)))
            }
            assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(down))
            assertEquals(listOf(PhysicalButtonGesture.LONG_PRESS to workspaceId(keyCode)), harness.actions)
        }
    }

    @Test fun upAndDownHaveIndependentTimersAndMappings() = runTest {
        val harness = Harness(this)
        val up = harness.down(24)
        harness.handle(up)
        advanceTimeBy(200)
        val down = harness.down(25)
        harness.handle(down)
        advanceTimeBy(400)
        assertEquals(PhysicalButtonKeyDecision.SHORT_PRESS, harness.release(down))
        advanceTimeBy(200)
        runCurrent()
        assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(up))
        assertEquals(listOf(PhysicalButtonGesture.SHORT_PRESS to "down-id",
            PhysicalButtonGesture.LONG_PRESS to "up-id"), harness.actions)
    }

    @Test fun matchingDevicesHaveIndependentPressesForTheSameKey() = runTest {
        val harness = Harness(this)
        val first = harness.down(24)
        harness.handle(first)
        advanceTimeBy(200)
        val second = harness.down(24).copy(deviceId = 8)
        harness.handle(second)
        advanceTimeBy(400)
        harness.release(second)
        advanceTimeBy(200)
        runCurrent()
        harness.release(first)
        assertEquals(listOf(PhysicalButtonGesture.SHORT_PRESS to "up-id",
            PhysicalButtonGesture.LONG_PRESS to "up-id"), harness.actions)
    }

    @Test fun mismatchedDescriptorAndPhoneAndOtherExternalDevice_passThrough() = runTest {
        for (keyCode in listOf(24, 25)) {
            val harness = Harness(this)
            for (event in listOf(
                harness.down(keyCode).copy(deviceDescriptor = "remote-descriptor "),
                harness.down(keyCode).copy(isExternal = false),
                harness.down(keyCode).copy(deviceId = 8, deviceDescriptor = "other-remote"),
                harness.down(keyCode).copy(isVirtual = true),
            )) {
                assertPassThrough(harness, event)
            }
            advanceTimeBy(1000)
            runCurrent()
            assertTrue(harness.actions.isEmpty())
        }
    }

    @Test fun disabledAndUnmappedAndDeletedWorkspace_passThrough() = runTest {
        for (keyCode in listOf(24, 25)) {
            val harness = Harness(this)
            val original = harness.configuration
            harness.configuration = original.copy(enabled = false)
            assertPassThrough(harness, harness.down(keyCode))
            harness.configuration = if (keyCode == 24) original.copy(volumeUpWorkspaceId = null)
                else original.copy(volumeDownWorkspaceId = null)
            assertPassThrough(harness, harness.down(keyCode))
            harness.configuration = original
            harness.available = emptySet()
            assertPassThrough(harness, harness.down(keyCode))
            advanceTimeBy(1000)
            runCurrent()
            assertTrue(harness.actions.isEmpty())
        }
    }

    @Test fun changedConfigurationDuringPress_blocksShortAndLongButConsumesRemainder() = runTest {
        for (keyCode in listOf(24, 25)) {
            for (holdMs in listOf(300L, 900L)) {
                val harness = Harness(this)
                val original = harness.configuration
                val changes = listOf(original.copy(enabled = false),
                    original.copy(deviceDescriptor = "other-remote"),
                    original.copy(volumeUpWorkspaceId = "another-id"),
                    original.copy(volumeDownWorkspaceId = null))
                for (changed in changes) {
                    harness.configuration = original
                    val down = harness.down(keyCode)
                    harness.handle(down)
                    harness.configuration = changed
                    advanceTimeBy(holdMs)
                    runCurrent()
                    assertEquals(PhysicalButtonKeyDecision.CONSUME,
                        harness.handle(down.copy(repeatCount = 1, eventTime = testScheduler.currentTime)))
                    assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(down))
                    assertTrue(harness.actions.isEmpty())
                }
            }
        }
    }

    @Test fun invalidatedConfigurationCannotReviveWhenRestored() = runTest {
        val harness = Harness(this)
        val original = harness.configuration
        val down = harness.down(24)
        harness.handle(down)
        harness.configuration = original.copy(enabled = false)
        harness.controller.invalidatePresses()
        harness.configuration = original
        advanceTimeBy(900)
        runCurrent()
        assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(down))
        assertTrue(harness.actions.isEmpty())
    }

    @Test fun deletedWorkspaceDuringHold_blocksLongPressAndConsumesUp() = runTest {
        val harness = Harness(this)
        val down = harness.down(25)
        harness.handle(down)
        harness.available = setOf("up-id")
        advanceTimeBy(800)
        runCurrent()
        assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(down))
        assertTrue(harness.actions.isEmpty())
    }

    @Test fun bindingStartsDuringHold_blocksPendingGesture() = runTest {
        val harness = Harness(this)
        val down = harness.down(24)
        harness.handle(down)
        harness.bindingRequested = true
        advanceTimeBy(800)
        runCurrent()
        assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(down))
        assertTrue(harness.actions.isEmpty())
    }

    @Test fun releaseExactlyAtThresholdBeforeTimerRuns_isOnlyLongPress() = runTest {
        val harness = Harness(this)
        val down = harness.down(24)
        harness.handle(down)
        advanceTimeBy(800)
        assertEquals(PhysicalButtonKeyDecision.LONG_PRESS, harness.release(down))
        runCurrent()
        assertEquals(listOf(PhysicalButtonGesture.LONG_PRESS to "up-id"), harness.actions)
    }

    @Test fun elapsedRepeatCanRecognizeLongBeforeTimerWithoutDuplicate() = runTest {
        val harness = Harness(this)
        val down = harness.down(24)
        harness.handle(down)
        advanceTimeBy(800)
        assertEquals(PhysicalButtonKeyDecision.LONG_PRESS,
            harness.handle(down.copy(repeatCount = 0, eventTime = testScheduler.currentTime)))
        runCurrent()
        harness.release(down)
        assertEquals(listOf(PhysicalButtonGesture.LONG_PRESS to "up-id"), harness.actions)
    }

    @Test fun staleReleaseAndReplacedTimer_doNotReleaseNewPress() = runTest {
        val harness = Harness(this)
        val old = harness.down(24)
        harness.handle(old)
        advanceTimeBy(200)
        val current = harness.down(24)
        harness.handle(current)
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, harness.release(old))
        advanceTimeBy(600)
        runCurrent()
        assertTrue(harness.actions.isEmpty())
        advanceTimeBy(200)
        runCurrent()
        harness.release(current)
        assertEquals(listOf(PhysicalButtonGesture.LONG_PRESS to "up-id"), harness.actions)
    }

    @Test fun bindingPressDoesNotEmitGestureAfterCapture() = runTest {
        val harness = Harness(this)
        harness.bindingRequested = true
        val down = harness.down(24)
        assertEquals(PhysicalButtonKeyDecision.BIND_REMOTE, harness.handle(down))
        harness.bindingRequested = false
        advanceTimeBy(900)
        runCurrent()
        assertEquals(PhysicalButtonKeyDecision.CONSUME, harness.release(down))
        assertTrue(harness.actions.isEmpty())
    }

    @Test fun clearCancelsPendingTimerWithoutLateGesture() = runTest {
        val harness = Harness(this)
        val down = harness.down(24)
        harness.handle(down)
        harness.controller.clear()
        advanceTimeBy(1000)
        runCurrent()
        assertTrue(harness.actions.isEmpty())
        assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, harness.release(down))
    }

    private fun assertPassThrough(harness: Harness, down: PhysicalButtonKeyEvent) {
        for (event in listOf(down, down.copy(repeatCount = 1), down.copy(action = 1))) {
            assertEquals(PhysicalButtonKeyDecision.PASS_THROUGH, harness.handle(event))
        }
    }

    private class Harness(private val scope: TestScope) {
        var configuration = PhysicalButtonConfiguration(true, "up-id", "down-id", "remote-descriptor")
        var available = setOf("up-id", "down-id")
        var bindingRequested = false
        val actions = mutableListOf<Pair<PhysicalButtonGesture, String?>>()
        val controller = PhysicalButtonGestureController(
            scope = scope,
            configuration = { configuration },
            availableWorkspaceIds = { available },
            bindingRequested = { bindingRequested },
            nowMillis = { scope.testScheduler.currentTime },
            onGesture = { gesture, event, snapshot -> actions += gesture to snapshot.workspaceForKey(event.keyCode) },
        )
        fun down(keyCode: Int) = PhysicalButtonKeyEvent(keyCode, 0, 0, 7,
            scope.testScheduler.currentTime, true, "remote-descriptor")
        fun handle(event: PhysicalButtonKeyEvent) = controller.handle(event)
        fun release(down: PhysicalButtonKeyEvent) = handle(down.copy(action = 1, eventTime = scope.testScheduler.currentTime))
    }

    private fun workspaceId(keyCode: Int) = if (keyCode == 24) "up-id" else "down-id"
}
