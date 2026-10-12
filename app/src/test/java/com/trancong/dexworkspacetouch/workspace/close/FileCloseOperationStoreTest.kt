package com.trancong.dexworkspacetouch.workspace.close

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

class FileCloseOperationStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun record() = CloseOperationRecord(PackageCloseRequest(UUID.randomUUID().toString(), listOf("example.target")), startedAt = 1,
        results = listOf(CloseTaskResult(CloseTask(1, 0, 2, -1, "example.target/.Main", "example.target/.Main", true), 1,
            CloseDispatch.DISPATCH_INTENT, CloseRemoveResult.UNKNOWN)))

    @Test fun committedIntentSurvivesNewStoreInstance() {
        val dir = temp.newFolder(); val r = record()
        FileCloseOperationStore(dir) {}.write(r)
        val restarted = FileCloseOperationStore(dir) {}
        assertEquals(r, restarted.read(r.request.operationId))
        assertEquals(listOf(r), restarted.pending())
    }
    @Test fun revisionConflictCannotOverwriteCommittedEvidence() {
        val dir = temp.newFolder(); val r = record(); val s = FileCloseOperationStore(dir) {}
        s.write(r)
        try { s.write(r.copy(results = emptyList())); fail() } catch (_: IllegalStateException) { }
        assertEquals(r, s.read(r.request.operationId))
    }
    @Test fun corruptJournalBlocksRecoveryInsteadOfPretendingEmpty() {
        val dir = temp.newFolder(); val r = record()
        File(dir, "${r.request.operationId}.json").writeText("{broken")
        try { FileCloseOperationStore(dir) {}.pending(); fail() } catch (_: Exception) { }
    }
    @Test fun directorySyncFailureIsNotAcknowledgedAsDurableIntent() {
        val dir = temp.newFolder(); val r = record()
        val s = FileCloseOperationStore(dir) { throw java.io.IOException("directory fsync failed") }
        try { s.write(r); fail("Intent acknowledged without durable directory entry") }
        catch (e: java.io.IOException) { assertEquals("directory fsync failed", e.message) }
    }
}
