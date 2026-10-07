package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.*
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class WorkspaceMutationAdmissionTest {
    @Test fun cancelledPendingResizeKeepsBothAdmissionsUntilServiceAcknowledges() =
        checkDelayedCancellation(cancelCaller = true, acknowledged = true)

    @Test fun timeoutKeepsAdmissionUntilPendingResizeCancellationIsAcknowledged() =
        checkDelayedCancellation(cancelCaller = false, acknowledged = true)

    @Test fun serviceDeathDuringCancellationKeepsAdmissionFailClosed() =
        checkDelayedCancellation(cancelCaller = true, acknowledged = false)

    @Test fun timeoutWithoutAcknowledgementKeepsAdmissionFailClosed() =
        checkDelayedCancellation(cancelCaller = false, acknowledged = false)

    private fun checkDelayedCancellation(cancelCaller: Boolean, acknowledged: Boolean) {
        val binding = DelayedCancellationBinding(acknowledged)
        val gate = EmbeddedProductRunGate()
        val arbiter = CarWorkflowExecutionArbiter()
        val returned = CountDownLatch(1)
        val transport = ProductionShizukuCommandTransport(binding)
        val shell = WorkspaceCommandShell { command ->
            transport.executeCommand(command, if (!cancelCaller && command.first() == "am") 100 else 3000).let {
                WorkspaceCommandResult(it.exitCode,it.stdout + "\n" + it.stderr)
            }
        }
        val caller = Thread {
            try { ExistingWorkspaceRepair(gate, arbiter, shell, pause = {}, pollAttempts = 3).run(request, snapshot) }
            finally { returned.countDown() }
        }
        try {
            caller.start()
            assertTrue(binding.pendingResize.await(2, TimeUnit.SECONDS))
            if (cancelCaller) caller.interrupt()
            assertTrue(binding.cancelDelivery.await(2, TimeUnit.SECONDS))
            assertFalse("Caller must retain admission while cancellation is unresolved", returned.await(300, TimeUnit.MILLISECONDS))
            assertFalse(gate.tryDispatchClassic { fail("Classic overlapped unresolved resize") })
            assertNull(gate.tryAcquireEmbedded("next"))
            assertFalse(arbiter.tryAcquire())
            binding.allowAcknowledgement.countDown()
            assertTrue(returned.await(2, TimeUnit.SECONDS))
            if (acknowledged) {
                assertTrue(gate.canEnterEmbedded())
                assertFalse(arbiter.isRunning.value)
                assertTrue(gate.tryDispatchClassic {})
                assertNotNull(gate.tryAcquireEmbedded("next"))
            } else {
                assertFalse(gate.canEnterEmbedded())
                assertNull(gate.createExecutionIfIdle { fail("Uncertain mutation allocated Embedded"); Any() })
                assertFalse(gate.tryDispatchClassic { fail("Uncertain mutation admitted Classic") })
                assertNull(gate.tryAcquireEmbedded("next"))
                assertFalse(arbiter.tryAcquire())
            }
            assertEquals(1, binding.resizeRequests.get())
        } finally {
            binding.allowAcknowledgement.countDown()
            caller.interrupt()
            caller.join(3000)
            transport.close()
        }
    }

    private class DelayedCancellationBinding(private val canAcknowledge: Boolean) : WorkspaceServiceBinding, WorkspaceCommandService {
        val pendingResize = CountDownLatch(1)
        val cancelDelivery = CountDownLatch(1)
        val allowAcknowledgement = CountDownLatch(1)
        val resizeRequests = AtomicInteger()
        private val stopped = CountDownLatch(1)
        @Volatile private var alive = true
        override fun availability(): CommandTransportFailure? = null
        override fun connect(timeoutMs: Long): WorkspaceCommandService = this
        override fun isAlive() = alive
        override fun open(arguments: List<String>, timeoutMs: Long, requestId: String): InputStream {
            val bytes = ByteArrayOutputStream()
            if (arguments == WorkspaceTaskCorrelation.DUMP_COMMAND) {
                WorkspaceCommandFrame.write(bytes, ShizukuCommandResult(0, dump, ""))
                return ByteArrayInputStream(bytes.toByteArray())
            }
            check(arguments.take(3) == listOf("am", "task", "resize"))
            resizeRequests.incrementAndGet()
            pendingResize.countDown()
            WorkspaceCommandFrame.writeFailure(bytes, CommandTransportFailure.CANCELLED)
            val frame = ByteArrayInputStream(bytes.toByteArray())
            return object : InputStream() {
                override fun read(): Int {
                    // A Binder/pipe read need not respond to Java thread interruption.
                    while (true) {
                        try { stopped.await(); break } catch (_: InterruptedException) { }
                    }
                    return frame.read()
                }
            }
        }
        override fun invalidate(requestId: String?): Future<Boolean> {
            if (requestId == null) { stopped.countDown(); return CompletableFuture.completedFuture(false) }
            val acknowledgement = CompletableFuture<Boolean>()
            Thread {
                cancelDelivery.countDown()
                allowAcknowledgement.await()
                // Service has consumed the cancellation and prevented the pending dispatch.
                alive = canAcknowledge
                stopped.countDown()
                acknowledgement.complete(canAcknowledge)
            }.apply { isDaemon = true }.start()
            return acknowledgement
        }
    }

    private companion object {
        val request = WorkspaceLaunchRequest("swc", "SWC", listOf(AppLaunchTarget("left",
            AppIdentity("com.example.calc", "com.example.calc.Main"), NormalizedBounds(0f, 0f, 0.5f, 1f), 0)))
        val snapshot = DisplayWorkAreaSnapshot(204, DisplayWorkArea(1920, 1200, insetTopPx = 25, insetBottomPx = 64),
            DiagnosticPixelBounds(0, 0, 1920, 1200), DiagnosticPixelBounds(0, 0, 1920, 1200), 1f, HostWindowMode.MAXIMIZED)
        val dump = "Display #204 (activities from top to bottom):\n" +
            "  * Task{abc #77 type=standard U=0 visible=true mode=freeform}\n" +
            "    mBounds=Rect(100, 100 - 800, 800)\n" +
            "      * Hist  #0: ActivityRecord{act77 u0 com.example.calc/com.example.calc.Main t77}\n" +
            "      Intent { cmp=com.example.calc/com.example.calc.Main }"
    }
}
