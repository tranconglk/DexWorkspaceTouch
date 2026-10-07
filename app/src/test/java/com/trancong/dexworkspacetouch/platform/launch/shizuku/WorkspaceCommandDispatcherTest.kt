package com.trancong.dexworkspacetouch.platform.launch.shizuku

import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class WorkspaceCommandDispatcherTest {
    private val dump = listOf("dumpsys", "activity", "activities")
    @Test fun cancellationClosesFrameAndAllowsNextRequestWithoutRetry() {
        val process = TestProcess()
        val starts = AtomicInteger()
        WorkspaceCommandDispatcher { starts.incrementAndGet(); process }.use { dispatcher ->
            val output = Capture()
            dispatcher.execute(dump, 3000, "first", output::stream)
            assertTrue(process.entered.await(1, TimeUnit.SECONDS))
            cancelExecuting(dispatcher, "first", process)
            assertTrue(output.closed.await(1, TimeUnit.SECONDS))
            failure(CommandTransportFailure.CANCELLED) { WorkspaceCommandFrame.read(output.input()) }
            assertTrue(process.destroyed)
            assertEquals(1, starts.get())
        }
    }
    @Test fun duplicateIdAndConcurrentMutationAreNeverLaunched() {
        val process = TestProcess()
        val starts = AtomicInteger()
        WorkspaceCommandDispatcher { starts.incrementAndGet(); process }.use { dispatcher ->
            val output = Capture()
            val resize = listOf("am", "task", "resize", "42", "8", "8", "472", "1016")
            dispatcher.execute(resize, 3000, "mutation", output::stream)
            assertTrue(process.entered.await(1, TimeUnit.SECONDS))
            failure(CommandTransportFailure.BUSY) { dispatcher.execute(resize, 3000, "second", Capture()::stream) }
            cancelExecuting(dispatcher, "mutation", process)
            assertTrue(output.closed.await(1, TimeUnit.SECONDS))
            // Completion may close the pipe just before releasing admission; wait only for this bounded cleanup.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
            while (true) {
                try { dispatcher.execute(resize, 3000, "mutation", Capture()::stream); fail("Repeated mutation") }
                catch(e: CommandTransportException) {
                    if (e.failure == CommandTransportFailure.INVALID_COMMAND) break
                    assertEquals(CommandTransportFailure.BUSY, e.failure)
                    assertTrue(System.nanoTime() < deadline)
                    Thread.yield()
                }
            }
            assertEquals(1, starts.get())
        }
    }
    @Test fun deadlineProducesFailureFrameAndKillsProcess() {
        val process = TestProcess()
        WorkspaceCommandDispatcher { process }.use { dispatcher ->
            val output = Capture()
            dispatcher.execute(dump, 30, "timeout", output::stream)
            assertTrue(output.closed.await(1, TimeUnit.SECONDS))
            failure(CommandTransportFailure.TIMEOUT) { WorkspaceCommandFrame.read(output.input()) }
            assertTrue(process.destroyed)
        }
    }
    @Test fun forbiddenCommandsNeverReachProcessFactory() {
        val starts = AtomicInteger()
        WorkspaceCommandDispatcher { starts.incrementAndGet(); TestProcess() }.use { dispatcher ->
            failure(CommandTransportFailure.INVALID_COMMAND) { dispatcher.execute(listOf("sh", "-c", "id"), 1000, "bad", Capture()::stream) }
        }
        assertEquals(0, starts.get())
    }
    @Test fun completedResultAllowsImmediateReadbackBeforePipeCloseFinishes() {
        val closing = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val first = ByteArrayOutputStream()
        WorkspaceCommandDispatcher { TestProcess(blocked = false) }.use { dispatcher ->
            try {
                dispatcher.execute(dump, 1000, "first") {
                    object : FilterOutputStream(first) {
                        override fun close() { closing.countDown(); allowClose.await(); super.close() }
                    }
                }
                assertTrue(closing.await(1, TimeUnit.SECONDS))
                assertEquals(0, WorkspaceCommandFrame.read(ByteArrayInputStream(first.toByteArray())).exitCode)
                val readback = Capture()
                dispatcher.execute(dump, 1000, "readback", readback::stream)
                allowClose.countDown()
                assertTrue(readback.closed.await(1, TimeUnit.SECONDS))
                assertEquals(0, WorkspaceCommandFrame.read(readback.input()).exitCode)
            } finally { allowClose.countDown() }
        }
    }
    @Test fun cancellationArrivingBeforeExecutionPreventsLateMutation() {
        val starts = AtomicInteger()
        WorkspaceCommandDispatcher { starts.incrementAndGet(); TestProcess(blocked = false) }.use { dispatcher ->
            dispatcher.cancel("cancelled-before-dispatch")
            failure(CommandTransportFailure.INVALID_COMMAND) {
                dispatcher.execute(listOf("am", "task", "resize", "42", "8", "8", "472", "1016"),
                    1000, "cancelled-before-dispatch", Capture()::stream)
            }
        }
        assertEquals(0, starts.get())
    }
    @Test fun executingCancellationAcknowledgesOnlyAfterKnownProcessCompletion() {
        val process = TestProcess()
        val cancelling = CountDownLatch(1)
        val acknowledged = CountDownLatch(1)
        WorkspaceCommandDispatcher { process }.use { dispatcher ->
            val output = Capture()
            dispatcher.execute(listOf("am", "task", "resize", "42", "8", "8", "472", "1016"),
                3000, "executing", output::stream)
            assertTrue(process.entered.await(1, TimeUnit.SECONDS))
            val cancellation = Thread {
                cancelling.countDown()
                dispatcher.cancel("executing")
                acknowledged.countDown()
            }
            try {
                cancellation.start()
                assertTrue(cancelling.await(1, TimeUnit.SECONDS))
                assertFalse("A cancellation reply must prove quiescence", acknowledged.await(300, TimeUnit.MILLISECONDS))
                assertFalse("Killing a shell process is not a known terminal command result", process.destroyed)
                process.complete()
                assertTrue(acknowledged.await(1, TimeUnit.SECONDS))
                assertTrue(output.closed.await(1, TimeUnit.SECONDS))
                failure(CommandTransportFailure.CANCELLED) { WorkspaceCommandFrame.read(output.input()) }
            } finally { process.complete(); cancellation.join(2000) }
        }
    }

    @Test fun acknowledgedPendingCancellationCannotDispatchAfterAdmissionRelease() {
        val closing = CountDownLatch(1)
        val allowWorker = CountDownLatch(1)
        val resizes = AtomicInteger()
        WorkspaceCommandDispatcher { arguments ->
            if (arguments.first() == "am") resizes.incrementAndGet()
            TestProcess(blocked = false)
        }.use { dispatcher ->
            try {
                dispatcher.execute(dump, 3000, "hold-worker") {
                    object : ByteArrayOutputStream() {
                        override fun close() { closing.countDown(); allowWorker.await(); super.close() }
                    }
                }
                assertTrue(closing.await(1, TimeUnit.SECONDS))
                val result = Capture()
                dispatcher.execute(listOf("am", "task", "resize", "42", "8", "8", "472", "1016"),
                    3000, "pending", result::stream)
                dispatcher.cancel("pending") // Successful synchronous reply permits admission release.
                allowWorker.countDown()
                assertTrue(result.closed.await(1, TimeUnit.SECONDS))
                failure(CommandTransportFailure.CANCELLED) { WorkspaceCommandFrame.read(result.input()) }
                assertEquals(0, resizes.get())
            } finally { allowWorker.countDown() }
        }
    }
    @Test fun killedShellExitCannotProveMutationCompletion() {
        WorkspaceCommandDispatcher { TestProcess(blocked = false, code = 137) }.use { dispatcher ->
            val result = Capture()
            dispatcher.execute(listOf("am", "task", "resize", "42", "8", "8", "472", "1016"),
                1000, "killed", result::stream)
            assertTrue(result.closed.await(1, TimeUnit.SECONDS))
            failure(CommandTransportFailure.EXECUTION_FAILED) { WorkspaceCommandFrame.read(result.input()) }
            failure(CommandTransportFailure.EXECUTION_FAILED) { dispatcher.cancel("killed") }
        }
    }
    private fun failure(reason: CommandTransportFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") } catch(e: CommandTransportException) { assertEquals(reason, e.failure) }
    }
    private fun cancelExecuting(dispatcher: WorkspaceCommandDispatcher, requestId: String, process: TestProcess) {
        val requested = CountDownLatch(1)
        val acknowledged = CountDownLatch(1)
        val caller = Thread { requested.countDown(); dispatcher.cancel(requestId); acknowledged.countDown() }
        try {
            caller.start()
            assertTrue(requested.await(1, TimeUnit.SECONDS))
            assertFalse(acknowledged.await(300, TimeUnit.MILLISECONDS))
            process.complete()
            assertTrue(acknowledged.await(1, TimeUnit.SECONDS))
        } finally { process.complete(); caller.join(2000) }
    }
    private class Capture {
        private val bytes = ByteArrayOutputStream()
        val closed = CountDownLatch(1)
        fun stream(): OutputStream = object : FilterOutputStream(bytes) { override fun close() { super.close(); closed.countDown() } }
        fun input() = ByteArrayInputStream(bytes.toByteArray())
    }
    private class TestProcess(private val blocked: Boolean = true, private val code: Int = 0) : Process() {
        val entered = CountDownLatch(1)
        private val stopped = CountDownLatch(1)
        @Volatile var destroyed = false
        override fun getInputStream() = ByteArrayInputStream("".toByteArray())
        override fun getErrorStream() = ByteArrayInputStream("".toByteArray())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor(): Int { entered.countDown(); if (blocked) stopped.await(); return code }
        override fun exitValue() = code
        override fun destroy() { destroyed = true; stopped.countDown() }
        fun complete() { stopped.countDown() }
    }
}
