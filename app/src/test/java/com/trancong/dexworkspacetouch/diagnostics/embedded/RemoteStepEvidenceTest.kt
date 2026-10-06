package com.trancong.dexworkspacetouch.diagnostics.embedded

import org.junit.Assert.*
import org.junit.Test

class RemoteStepEvidenceTest {
    @Test fun injectedStagesRetainOriginalExceptionAndSecondaryRollbackFailure() {
        val recorder = EvidenceRecorder("remote", 23, 2000, "remote")
        EmbeddedEvidence.installRemote(recorder)
        val first = IllegalStateException("Received Surface became invalid")
        val second = IllegalArgumentException("VDM cleanup failed")
        val calls = mutableListOf<String>()
        try {
            val start = runCatching { EmbeddedEvidence.remoteStep("012-sid", "display_create") { calls += "start"; throw first } }
            val cleanup = runCatching { EmbeddedEvidence.remoteStep("012-sid", "rollback_association") { calls += "rollback"; throw second } }
            assertSame(first, start.exceptionOrNull()); assertSame(second, cleanup.exceptionOrNull())
            assertEquals(listOf("start", "rollback"), calls)
            val failures = recorder.drain().filter { it.event == "step.failure" }
            assertEquals("Original and secondary failure evidence missing", 2, failures.size)
            assertEquals(listOf("display_create", "rollback_association"), failures.map { it.fields["stage"] })
            assertEquals(listOf("java.lang.IllegalStateException", "java.lang.IllegalArgumentException"),
                failures.map { it.fields["exception_type"] })
            assertTrue(failures.all { it.sid == "012-sid" && it.pid == 23 && it.uid == 2000 })
        } finally { EmbeddedEvidence.installRemote(null) }
    }
}
