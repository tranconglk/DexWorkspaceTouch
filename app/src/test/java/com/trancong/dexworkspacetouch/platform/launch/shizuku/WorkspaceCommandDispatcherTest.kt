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
            dispatcher.cancel("first")
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
            dispatcher.cancel("mutation")
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
    private fun failure(reason: CommandTransportFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") } catch(e: CommandTransportException) { assertEquals(reason, e.failure) }
    }
    private class Capture {
        private val bytes = ByteArrayOutputStream()
        val closed = CountDownLatch(1)
        fun stream(): OutputStream = object : FilterOutputStream(bytes) { override fun close() { super.close(); closed.countDown() } }
        fun input() = ByteArrayInputStream(bytes.toByteArray())
    }
    private class TestProcess(private val blocked: Boolean = true) : Process() {
        val entered = CountDownLatch(1)
        private val stopped = CountDownLatch(1)
        @Volatile var destroyed = false
        override fun getInputStream() = ByteArrayInputStream("".toByteArray())
        override fun getErrorStream() = ByteArrayInputStream("".toByteArray())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor(): Int { entered.countDown(); if (blocked) stopped.await(); return 0 }
        override fun exitValue() = 0
        override fun destroy() { destroyed = true; stopped.countDown() }
    }
}
