package com.trancong.dexworkspacetouch.platform.launch.shizuku

import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ProductionShizukuCommandTransportTest {
    private val dump = listOf("dumpsys", "activity", "activities")
    private val resize = listOf("am", "task", "resize", "42", "8", "8", "472", "1016")

    @Test fun readOnlyCapturesStdoutStderrAndExitSeparately() {
        val binding = Binding(ShizukuCommandResult(7, "stdout", "stderr"))
        ProductionShizukuCommandTransport(binding).use {
            assertEquals(ShizukuCommandResult(7, "stdout", "stderr"), it.executeCommand(dump))
        }
        assertEquals(listOf(dump), binding.commands)
    }
    @Test fun successfulResizeIsExecutedOnce() {
        val binding = Binding()
        ProductionShizukuCommandTransport(binding).use { assertEquals(0, it.executeCommand(resize).exitCode) }
        assertEquals(listOf(resize), binding.commands)
    }
    @Test fun preservesExistingEngineOutputFraming() {
        ProductionShizukuCommandTransport(Binding(ShizukuCommandResult(1, "out", "Error: rejected"))).use {
            assertEquals(WorkspaceCommandResult(1, "out\nError: rejected"), it.execute(resize))
        }
    }
    @Test fun unavailableShizukuDoesNotBindOrExecute() = unavailable(CommandTransportFailure.SHIZUKU_UNAVAILABLE)
    @Test fun permissionDeniedDoesNotBindOrExecute() = unavailable(CommandTransportFailure.PERMISSION_DENIED)
    @Test fun unsupportedUidDoesNotExecute() = unavailable(CommandTransportFailure.UID_UNSUPPORTED)
    private fun unavailable(reason: CommandTransportFailure) {
        val binding = Binding().apply { unavailable = reason }
        ProductionShizukuCommandTransport(binding).use { failure(reason) { it.executeCommand(resize) } }
        assertEquals(0, binding.connects)
        assertTrue(binding.commands.isEmpty())
    }
    @Test fun bindFailureIsMappedWithoutMutation() {
        val binding = Binding().apply { connectFailure = CommandTransportFailure.BIND_FAILED }
        ProductionShizukuCommandTransport(binding).use { failure(CommandTransportFailure.BIND_FAILED) { it.executeCommand(resize) } }
        assertTrue(binding.commands.isEmpty())
    }
    @Test fun serviceDeathAfterResultIsNotSuccessAndDoesNotRetry() {
        val binding = Binding().apply { alive = false }
        ProductionShizukuCommandTransport(binding).use { failure(CommandTransportFailure.SERVICE_DIED) { it.executeCommand(resize) } }
        assertEquals(listOf(resize), binding.commands)
    }
    @Test fun disconnectedCallFailsThenNewRequestMayReconnect() {
        val binding = Binding().apply { openFailure = CommandTransportFailure.DISCONNECTED }
        ProductionShizukuCommandTransport(binding).use {
            failure(CommandTransportFailure.DISCONNECTED) { it.executeCommand(resize) }
            binding.openFailure = null
            assertEquals(0, it.executeCommand(dump).exitCode)
        }
        assertEquals(listOf(resize, dump), binding.commands)
    }
    @Test fun deadlineIncludesBindingAndNeverDispatchesExpiredMutation() {
        val binding = Binding().apply { connectDelay = 200 }
        ProductionShizukuCommandTransport(binding).use { failure(CommandTransportFailure.TIMEOUT) { it.executeCommand(resize, 30) } }
        assertTrue(binding.commands.isEmpty())
    }
    @Test fun timeoutDoesNotRetryMutation() {
        val binding = Binding().apply { block = true }
        ProductionShizukuCommandTransport(binding).use { failure(CommandTransportFailure.TIMEOUT) { it.executeCommand(resize, 40) } }
        assertEquals(listOf(resize), binding.commands)
        assertTrue(binding.invalidations.get() > 0)
    }
    @Test fun cancellationInvalidatesOutstandingCommandAndDoesNotRetry() {
        val binding = Binding().apply { block = true }
        val transport = ProductionShizukuCommandTransport(binding)
        var reason: CommandTransportFailure? = null
        val caller = Thread { try { transport.executeCommand(resize) } catch (e: CommandTransportException) { reason = e.failure } }
        caller.start()
        assertTrue(binding.entered.await(1, java.util.concurrent.TimeUnit.SECONDS))
        caller.interrupt(); caller.join(1000)
        assertFalse(caller.isAlive)
        assertEquals(CommandTransportFailure.CANCELLED, reason)
        assertEquals(listOf(resize), binding.commands)
        transport.close()
    }
    @Test fun malformedFrameFailsClosed() {
        val binding = Binding().apply { malformed = true }
        ProductionShizukuCommandTransport(binding).use { failure(CommandTransportFailure.MALFORMED_RESULT) { it.executeCommand(dump) } }
    }
    @Test fun deadServiceWithBrokenPipeMapsToServiceDeath() {
        val binding = Binding().apply { malformed = true; alive = false }
        ProductionShizukuCommandTransport(binding).use {
            failure(CommandTransportFailure.SERVICE_DIED) { it.executeCommand(resize) }
        }
        assertEquals(listOf(resize), binding.commands)
    }
    @Test fun closePreventsFurtherCommands() {
        val binding = Binding()
        val transport = ProductionShizukuCommandTransport(binding)
        transport.close()
        failure(CommandTransportFailure.CLOSED) { transport.executeCommand(dump) }
        assertTrue(binding.commands.isEmpty())
    }
    @Test fun whitelistRejectsLaunchShellAndMalformedResizeBeforeBinding() {
        val invalid = listOf(listOf("sh", "-c", "id"), listOf("am", "start", "-n", "pkg/.App"),
            listOf("getprop", "ro.build.version.oneui"), dump + "--user", resize + "extra",
            resize.toMutableList().apply { this[3] = "42;id" }, resize.toMutableList().apply { this[3] = "0" },
            resize.toMutableList().apply { this[4] = "-1" }, resize.toMutableList().apply { this[6] = "8" })
        val binding = Binding()
        ProductionShizukuCommandTransport(binding).use { transport -> invalid.forEach {
            failure(CommandTransportFailure.INVALID_COMMAND) { transport.executeCommand(it) }
        } }
        assertEquals(0, binding.connects)
    }
    @Test fun framePreservesLargeDumpWithoutBinderSizedTruncation() {
        val result = ShizukuCommandResult(0, "task record\n".repeat(100000), "warning")
        val output = ByteArrayOutputStream()
        WorkspaceCommandFrame.write(output, result)
        assertEquals(result, WorkspaceCommandFrame.read(ByteArrayInputStream(output.toByteArray())))
    }
    @Test fun frameRejectsTruncatedOrOversizedLengths() {
        val output = ByteArrayOutputStream()
        WorkspaceCommandFrame.write(output, ShizukuCommandResult(0, "hello", ""))
        failure(CommandTransportFailure.MALFORMED_RESULT) {
            WorkspaceCommandFrame.read(ByteArrayInputStream(output.toByteArray().dropLast(2).toByteArray()))
        }
    }

    private fun failure(reason: CommandTransportFailure, action: () -> Unit) {
        try { action(); fail("Expected $reason") } catch (e: CommandTransportException) { assertEquals(reason, e.failure) }
    }
    private class Binding(private val result: ShizukuCommandResult = ShizukuCommandResult(0, "", "")) : WorkspaceServiceBinding, WorkspaceCommandService {
        var unavailable: CommandTransportFailure? = null
        var connectFailure: CommandTransportFailure? = null
        var openFailure: CommandTransportFailure? = null
        var connectDelay = 0L
        var connects = 0
        var alive = true
        var block = false
        var malformed = false
        val entered = CountDownLatch(1)
        val invalidations = AtomicInteger()
        val commands = java.util.Collections.synchronizedList(mutableListOf<List<String>>())
        override fun availability() = unavailable
        override fun connect(timeoutMs: Long): WorkspaceCommandService {
            connects++
            connectFailure?.let { throw CommandTransportException(it) }
            Thread.sleep(connectDelay)
            return this
        }
        override fun open(arguments: List<String>, timeoutMs: Long, requestId: String): InputStream {
            commands += arguments
            entered.countDown()
            openFailure?.let { throw CommandTransportException(it) }
            if (block) CountDownLatch(1).await()
            if (malformed) return ByteArrayInputStream(byteArrayOf(1, 2, 3))
            val output = ByteArrayOutputStream()
            WorkspaceCommandFrame.write(output, result)
            return ByteArrayInputStream(output.toByteArray())
        }
        override fun isAlive() = alive
        override fun invalidate(requestId: String?) { invalidations.incrementAndGet() }
    }
}
