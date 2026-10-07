package com.trancong.dexworkspacetouch.platform.launch.shizuku

import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class WorkspaceCommandDispatcher(private val startProcess: (List<String>) -> Process = { ProcessBuilder(it).start() }) : AutoCloseable {
    private class Job(val id: String, val output: OutputStream, val deadline: Long, val mutating: Boolean) {
        var cancelled = false
        var dispatched = false
        var knownTerminal = false
        var forcedTeardown = false
        val finished = CountDownLatch(1)
        var runner: Thread? = null
        var process: Process? = null
    }
    private val lock = Any()
    private val seen = HashMap<String, Job?>()
    private var active: Job? = null
    private var closed = false
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "DWT.WorkspaceShell").apply { isDaemon = true } }

    fun execute(arguments: List<String>, timeoutMs: Long, requestId: String, output: () -> OutputStream) {
        WorkspaceCommandWhitelist.validate(arguments)
        if (timeoutMs !in 1..8000 || !requestId.matches(Regex("[A-Za-z0-9-]{1,64}")))
            throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        synchronized(lock) {
            if (closed) throw CommandTransportException(CommandTransportFailure.CLOSED)
            if (active != null) throw CommandTransportException(CommandTransportFailure.BUSY)
            // Bound replay memory. Rebinding is allowed only for a new command; never replay a failed mutation.
            if (seen.size >= 4096 || seen.containsKey(requestId)) throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
            val job = Job(requestId, output(), deadline, arguments.first() == "am")
            seen[requestId] = job
            active = job
            worker.execute {
                try {
                    job.output.use { stream ->
                        var failure: CommandTransportFailure? = null
                        val result = try {
                            synchronized(lock) {
                                job.runner = Thread.currentThread()
                                if (job.cancelled || Thread.currentThread().isInterrupted)
                                    throw CommandTransportException(CommandTransportFailure.CANCELLED)
                                if (System.nanoTime() >= deadline) throw CommandTransportException(CommandTransportFailure.TIMEOUT)
                                // Admission and cancellation are checked before the only dispatch. No interpreter.
                                job.dispatched = true
                                job.process = startProcess(arguments.toList())
                            }
                            awaitWorkspaceCommandProcess(job.process!!,
                                TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1),
                                onKnownExit = { exit -> synchronized(lock) {
                                    // Android encodes signal termination as 128 + signal, not a command reply.
                                    if (!job.forcedTeardown && exit in 0..127) job.knownTerminal = true
                                } }).also {
                                synchronized(lock) {
                                    if (job.cancelled) throw CommandTransportException(CommandTransportFailure.CANCELLED)
                                }
                            }
                        } catch (e: Exception) {
                            failure = (e as? CommandTransportException)?.failure ?: CommandTransportFailure.EXECUTION_FAILED
                            null
                        }
                        job.process?.destroyForcibly()
                        // A complete frame is permission for the caller to issue readback immediately.
                        // Release execution admission BEFORE publishing it, not after closing the pipe.
                        synchronized(lock) {
                            if (!job.dispatched) job.knownTerminal = true
                            job.runner = null
                            if (active === job) active = null
                            job.finished.countDown()
                        }
                        if (result != null && (!job.mutating || job.knownTerminal)) WorkspaceCommandFrame.write(stream, result)
                        else if (result != null) WorkspaceCommandFrame.writeFailure(stream, CommandTransportFailure.EXECUTION_FAILED)
                        else WorkspaceCommandFrame.writeFailure(stream, failure!!)
                    }
                } finally {
                    job.process?.destroyForcibly()
                    synchronized(lock) {
                        if (!job.dispatched) job.knownTerminal = true
                        job.runner = null
                        job.process = null
                        if (active === job) active = null
                        job.finished.countDown()
                    }
                    // Cancellation must not leak an interrupt into a later command on this worker.
                    Thread.interrupted()
                }
            }
        }
    }
    /** Normal synchronous reply proves no pending dispatch or a known natural process exit. */
    fun cancel(requestId: String) {
        if (!requestId.matches(Regex("[A-Za-z0-9-]{1,64}")))
            throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
        val job = synchronized(lock) {
            if (!seen.containsKey(requestId)) {
                if (seen.size >= 4096) closed = true else seen[requestId] = null
                return // Tombstone or closed service prevents any later dispatch.
            }
            val current = seen[requestId] ?: return
            current.cancelled = true
            if (!current.dispatched || current.knownTerminal) return
            current
        }
        val remaining = job.deadline - System.nanoTime()
        if (remaining > 0) job.finished.await(remaining, TimeUnit.NANOSECONDS)
        synchronized(lock) {
            if (!job.knownTerminal) throw CommandTransportException(CommandTransportFailure.EXECUTION_FAILED)
        }
    }
    override fun close() = synchronized(lock) {
        closed = true
        active?.let {
            it.cancelled = true
            it.forcedTeardown = true
            it.runner?.interrupt()
            it.process?.destroyForcibly()
        }
        // Let an admitted job run its cancellation/finally even if it has not started yet.
        worker.shutdown()
        Unit
    }
}
