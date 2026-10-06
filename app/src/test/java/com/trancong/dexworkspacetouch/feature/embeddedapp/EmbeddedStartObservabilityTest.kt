package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence
import com.trancong.dexworkspacetouch.diagnostics.embedded.EvidenceRecorder
import com.trancong.dexworkspacetouch.diagnostics.embedded.EvidenceLimits

/** Real IPC boundary, with an optional reflective recorder until 012 exists. */
class EmbeddedStartObservabilityTest {
    @Test fun lateIpcFailureLeavesIdenticalLifecycleForDisabledFullAndThrowingSinks() {
        val full = EvidenceRecorder("app", 1, 2, "epoch", EvidenceLimits(queueRecords = 1)).also { it.emit("occupied") }
        val throwing = object : EvidenceRecorder("app", 1, 2, "epoch") {
            override fun emit(event: String, sid: String?, cell: String?, graph: String?, fields: Map<String, String>) { error("sink unavailable") }
        }
        for (sink in listOf(null, EvidenceRecorder("app", 1, 2, "epoch"), full, throwing)) {
            EmbeddedEvidence.installApp(sink)
            val lane = ManualLane(); val fixture = StartBoundaryFixture(lane)
            try {
                fixture.ready(fakeStartService(start = { throw IllegalStateException("Received Surface became invalid") }))
                fixture.session.startSession(validRawSurface())
                val task = lane.tasks.single()
                fixture.session.close(); fixture.worker.drain(); fixture.main.drain(); fixture.finalization.drain()
                fixture.session.detachNotifications()
                val before = fixture.snapshots.toList()
                lane.drain(); fixture.completion.drain(); fixture.main.drain(); fixture.finalization.drain()
                assertEquals(before, fixture.snapshots)
                assertEquals(EmbeddedSessionPhase.CLEANUP_INCOMPLETE, fixture.phases.last())
                assertEquals(0, fixture.transport.removals)
                assertDetachedStartTask(task, fixture.session)
            } finally { fixture.close(); EmbeddedEvidence.installApp(null) }
        }
    }
    @Test fun distinctIpcExceptionsRetainOriginalCauseBeforeGenericCompletion() {
        val first = capture("Received Surface became invalid")
        val second = capture("VDM input configuration did not settle to touchscreen=finger")
        assertEquals(first.first, second.first)
        assertEquals("IPC exception must be retained before START_IPC_FAILED", 1, first.second.size)
        assertEquals(1, second.second.size)
        assertNotEquals(first.second.single().getJSONObject("fields").getString("message"),
            second.second.single().getJSONObject("fields").getString("message"))
    }

    @Test fun detachedCompletionIsObservedWithoutRevivingTheSession() {
        val recorder = installCapture()
        val lane = ManualLane()
        val fixture = StartBoundaryFixture(lane)
        try {
            fixture.ready(fakeStartService(start = { throw IllegalStateException("Received Surface became invalid") }))
            fixture.session.startSession(validRawSurface())
            savedTask = lane.tasks.single()
            fixture.session.detachNotifications()
            val phases = fixture.phases.toList()
            lane.drain(); fixture.completion.drain(); fixture.main.drain()
            assertEquals(phases, fixture.phases)
            assertDetachedStartTask(savedTask, fixture.session)
            val rows = rows(recorder)
            assertTrue("IPC evidence must survive mailbox detach", rows.any { it.getString("event") == "ipc.failure" })
            assertTrue(rows.any { it.getString("event") == "ipc.mailbox" &&
                it.getJSONObject("fields").getString("admitted") == "false" })
        } finally { fixture.close(); uninstall() }
    }

    private lateinit var savedTask: Runnable
    private fun capture(message: String): Pair<EmbeddedSessionFailure?, List<JSONObject>> {
        val recorder = installCapture()
        val lane = ManualLane()
        val fixture = StartBoundaryFixture(WorkAdmission { savedTask = it; lane.tryExecute(it) })
        try {
            fixture.ready(fakeStartService(start = { throw IllegalStateException(message) }))
            fixture.session.startSession(validRawSurface())
            lane.drain(); fixture.completion.drain(); fixture.main.drain()
            return fixture.snapshots.last().failure to rows(recorder).filter { it.getString("event") == "ipc.failure" }
        } finally { fixture.close(); uninstall() }
    }

    private fun installCapture(): Any? = try {
        val type = Class.forName("com.trancong.dexworkspacetouch.diagnostics.embedded.EvidenceRecorder")
        val recorder = type.getConstructor(String::class.java, Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType, String::class.java).newInstance("app", 12, 10001, "012-test")
        val facade = Class.forName("com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence")
        facade.getMethod("installApp", type).invoke(facade.getField("INSTANCE").get(null), recorder)
        recorder
    } catch (_: ClassNotFoundException) { null }

    private fun rows(recorder: Any?): List<JSONObject> = if (recorder == null) emptyList() else {
        @Suppress("UNCHECKED_CAST")
        val events = recorder.javaClass.getMethod("drain").invoke(recorder) as List<Any>
        events.map { JSONObject(it.javaClass.getMethod("toJson").invoke(it) as String) }
    }
    private fun uninstall() {
        runCatching {
            val facade = Class.forName("com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence")
            val type = Class.forName("com.trancong.dexworkspacetouch.diagnostics.embedded.EvidenceRecorder")
            facade.getMethod("installApp", type).invoke(facade.getField("INSTANCE").get(null), null)
        }
    }
}
