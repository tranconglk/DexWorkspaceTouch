package com.trancong.dexworkspacetouch.workspace.close

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class VerifiedTaskRemoverTest {
    private val task = CloseTask(1, 0, 0, 99, "example.target/.Main", "example.target/.Main", true)
    private val request = PackageCloseRequest(UUID.randomUUID().toString(), listOf("example.target"))
    private class Control(var tasks: List<CloseTask>) : CloseTaskControl {
        var complete = true
        val removed = mutableListOf<Int>()
        override fun enumerate() = CloseSnapshot(tasks, complete, 1, 0, "fake", "fake")
        override fun removeTask(taskId: Int): Boolean { removed += taskId; return true }
    }
    @Test fun changedIdentityIsNeverRemoved() {
        val c = Control(listOf(task.copy(topActivity = "example.target/.Other")))
        val r = VerifiedTaskRemover(request, c) { true }.remove(task)
        assertEquals(CloseRemoveResult.IDENTITY_CHANGED, r.result)
        assertTrue(c.removed.isEmpty())
    }
    @Test fun displayMoveDoesNotChangeAllDisplayIdentity() {
        val c = Control(listOf(task.copy(displayId = 2)))
        val r = VerifiedTaskRemover(request, c) { true }.remove(task)
        assertEquals(CloseRemoveResult.REMOVE_TRUE, r.result)
        assertEquals(2, r.identityAtRemove!!.displayId)
        assertEquals(listOf(1), c.removed)
    }
    @Test fun protectedRequestFailsSafeAtExecutor() {
        val c = Control(listOf(task))
        val r = VerifiedTaskRemover(request.copy(packagesRequested = listOf(DWT_PROTECTED_PACKAGE)), c) { true }.remove(task)
        assertEquals(CloseRemoveResult.UNSUPPORTED_TASK, r.result)
        assertTrue(c.removed.isEmpty())
    }
    @Test fun missingTaskReportsAlreadyGoneWithoutMutation() {
        val c = Control(emptyList())
        assertEquals(CloseRemoveResult.ALREADY_GONE, VerifiedTaskRemover(request, c) { true }.remove(task).result)
        assertTrue(c.removed.isEmpty())
    }
    @Test fun revokeBetweenEnumerationAndRemoveFailsClosed() {
        val c = Control(listOf(task)); var checks = 0
        try { VerifiedTaskRemover(request, c) { ++checks == 1 }.remove(task); fail() }
        catch (e: CloseBackendException) { assertEquals("BACKEND_LOST", e.reason) }
        assertTrue(c.removed.isEmpty())
    }
    @Test fun containerAndIncompleteEnumerationAreNeverRemoved() {
        val c = Control(listOf(task.copy(leaf = false)))
        assertEquals(CloseRemoveResult.UNSUPPORTED_TASK, VerifiedTaskRemover(request, c) { true }.remove(task.copy(leaf = false)).result)
        c.complete = false
        try { VerifiedTaskRemover(request, c) { true }.remove(task); fail() } catch (_: CloseBackendException) { }
        assertTrue(c.removed.isEmpty())
    }
    @Test fun identityChangedInsideFinalGateIsCheckedAgainBeforeRemove() {
        val c = Control(listOf(task)); var gates = 0
        val result = VerifiedTaskRemover(request, c) {
            if (++gates == 2) c.tasks = listOf(task.copy(topActivity = "another.pkg/.Main"))
            true
        }.remove(task)
        assertEquals(CloseRemoveResult.IDENTITY_CHANGED, result.result)
        assertTrue(c.removed.isEmpty())
    }
}
