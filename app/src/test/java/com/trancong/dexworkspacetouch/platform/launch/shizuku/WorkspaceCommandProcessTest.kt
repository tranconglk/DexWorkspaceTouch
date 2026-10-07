package com.trancong.dexworkspacetouch.platform.launch.shizuku

import java.io.*
import java.util.concurrent.*
import org.junit.Assert.*
import org.junit.Test

class WorkspaceCommandProcessTest {
    @Test fun drainsBothStreamsAndPreservesNonzeroExit() {
        val process = FakeProcess()
        assertEquals(ShizukuCommandResult(7, "stdout", "stderr"), awaitWorkspaceCommandProcess(process, 1000))
        assertTrue(process.destroyed)
    }
    @Test fun timeoutKillsOwnedProcess() {
        val process = FakeProcess(blocked = true)
        failure(CommandTransportFailure.TIMEOUT) { awaitWorkspaceCommandProcess(process, 30) }
        assertTrue(process.destroyed)
    }
    @Test fun cancellationKillsOwnedProcess() {
        val process = FakeProcess(blocked = true)
        var reason: CommandTransportFailure? = null
        val caller = Thread { try { awaitWorkspaceCommandProcess(process, 3000) } catch(e: CommandTransportException) { reason = e.failure } }
        caller.start(); assertTrue(process.entered.await(1, TimeUnit.SECONDS)); caller.interrupt(); caller.join(1000)
        assertEquals(CommandTransportFailure.CANCELLED, reason)
        assertTrue(process.destroyed)
    }
    @Test fun oversizedOutputFailsRatherThanTruncating() {
        val process = FakeProcess(stdout = "x".repeat(2_000_001))
        failure(CommandTransportFailure.OUTPUT_LIMIT) { awaitWorkspaceCommandProcess(process, 1000) }
        assertTrue(process.destroyed)
    }
    private fun failure(reason: CommandTransportFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") } catch(e: CommandTransportException) { assertEquals(reason, e.failure) }
    }
    private class FakeProcess(private val blocked: Boolean = false, stdout: String = "stdout") : Process() {
        val entered = CountDownLatch(1)
        private val stopped = CountDownLatch(1)
        @Volatile var destroyed = false
        private val out = ByteArrayInputStream(stdout.toByteArray())
        override fun getInputStream() = out
        override fun getErrorStream() = ByteArrayInputStream("stderr".toByteArray())
        override fun getOutputStream() = ByteArrayOutputStream()
        override fun waitFor(): Int { entered.countDown(); if(blocked) stopped.await(); return 7 }
        override fun exitValue() = 7
        override fun destroy() { destroyed = true; stopped.countDown() }
    }
}
