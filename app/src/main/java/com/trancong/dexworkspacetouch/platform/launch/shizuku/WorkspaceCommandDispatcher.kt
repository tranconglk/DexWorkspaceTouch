package com.trancong.dexworkspacetouch.platform.launch.shizuku

import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class WorkspaceCommandDispatcher(private val startProcess: (List<String>) -> Process = { ProcessBuilder(it).start() }) : AutoCloseable {
    private class Job(val id: String, val output: OutputStream) {
        var cancelled = false
        var runner: Thread? = null
        var process: Process? = null
    }
    private val lock = Any()
    private val seen = HashSet<String>()
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
            if (seen.size >= 4096 || !seen.add(requestId)) throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
            val job = Job(requestId, output())
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
                                job.process = startProcess(arguments.toList())
                            }
                            awaitWorkspaceCommandProcess(job.process!!,
                                TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()).coerceAtLeast(1)).also {
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
                        synchronized(lock) { job.runner = null; if (active === job) active = null }
                        if (result != null) WorkspaceCommandFrame.write(stream, result)
                        else WorkspaceCommandFrame.writeFailure(stream, failure!!)
                    }
                } finally {
                    job.process?.destroyForcibly()
                    synchronized(lock) { job.runner = null; if (active === job) active = null }
                    // Cancellation must not leak an interrupt into a later command on this worker.
                    Thread.interrupted()
                }
            }
        }
    }
    fun cancel(requestId: String) = synchronized(lock) {
        if (!requestId.matches(Regex("[A-Za-z0-9-]{1,64}")))
            throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
        // Binder can deliver cancel before execute. Remember that ID for this service lifetime.
        if (seen.size >= 4096 && requestId !in seen) closed = true else seen.add(requestId)
        active?.takeIf { it.id == requestId }?.let {
            it.cancelled = true
            it.runner?.interrupt()
            it.process?.destroyForcibly()
        }
        Unit
    }
    override fun close() = synchronized(lock) {
        closed = true
        active?.let { cancel(it.id) }
        // Let an admitted job run its cancellation/finally even if it has not started yet.
        worker.shutdown()
        Unit
    }
}
