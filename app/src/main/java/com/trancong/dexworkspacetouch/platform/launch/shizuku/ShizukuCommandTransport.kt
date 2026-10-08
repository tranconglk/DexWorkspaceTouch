package com.trancong.dexworkspacetouch.platform.launch.shizuku

import java.io.*
import java.util.UUID
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

internal enum class CommandTransportFailure {
    SHIZUKU_UNAVAILABLE, PERMISSION_DENIED, UID_UNSUPPORTED, BIND_FAILED, DISCONNECTED,
    SERVICE_DIED, TIMEOUT, CANCELLED, INVALID_COMMAND, MALFORMED_RESULT, OUTPUT_LIMIT,
    EXECUTION_FAILED, BUSY, CLOSED
}
internal open class CommandTransportException(val failure: CommandTransportFailure, cause: Throwable? = null) :
    IllegalStateException(failure.name, cause)

/** Local admission signal; never encoded as a new wire-protocol result. */
internal class UncertainWorkspaceMutation(cause: CommandTransportException) :
    CommandTransportException(cause.failure, cause)

internal data class ShizukuCommandResult(val exitCode: Int, val stdout: String, val stderr: String)
internal interface WorkspaceCommandService {
    fun open(arguments: List<String>, timeoutMs: Long, requestId: String): InputStream
    fun isAlive(): Boolean
}
/** Invalidation is immediate; true completes only after a synchronous service cancellation acknowledgement. */
internal interface WorkspaceServiceBinding {
    fun availability(): CommandTransportFailure?
    fun connect(timeoutMs: Long): WorkspaceCommandService
    fun invalidate(requestId: String?): Future<Boolean>
}

internal class ProductionShizukuCommandTransport(private val binding: WorkspaceServiceBinding) : WorkspaceCommandShell, AutoCloseable {
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1),
        ThreadFactory { Thread(it, "DWT.WorkspaceCommand").apply { isDaemon = true } })
    private class Attempt {
        var cancelled = false
        var crossed = false
        var knownTerminal = false
    }
    private val active = AtomicReference<Attempt?>()
    @Volatile private var closed = false

    fun runtimeState(): ShizukuRuntimeState =
        (if (closed) CommandTransportFailure.CLOSED else binding.availability()).toShizukuRuntimeState()

    fun executeCommand(arguments: List<String>, timeoutMs: Long = 3000): ShizukuCommandResult {
        if (closed) throw CommandTransportException(CommandTransportFailure.CLOSED)
        WorkspaceCommandWhitelist.validate(arguments)
        require(timeoutMs in 1..8000)
        if (Thread.currentThread().isInterrupted) throw CommandTransportException(CommandTransportFailure.CANCELLED)
        val attempt = Attempt()
        if (!active.compareAndSet(null, attempt)) throw CommandTransportException(CommandTransportFailure.BUSY)
        val mutating = arguments.first() == "am"
        val requestId = UUID.randomUUID().toString()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        fun remaining(): Long {
            val nanos = deadline - System.nanoTime()
            if (nanos <= 0) throw CommandTransportException(CommandTransportFailure.TIMEOUT)
            if (Thread.currentThread().isInterrupted || closed) throw CommandTransportException(CommandTransportFailure.CANCELLED)
            return TimeUnit.NANOSECONDS.toMillis(nanos).coerceAtLeast(1)
        }
        val future = try {
            worker.submit(Callable {
                try {
                    binding.availability()?.let { throw CommandTransportException(it) }
                    val service = binding.connect(remaining())
                    val result = try {
                        val budget = remaining()
                        synchronized(attempt) {
                            if (attempt.cancelled || closed) throw CommandTransportException(CommandTransportFailure.CANCELLED)
                            attempt.crossed = true
                        }
                        service.open(arguments.toList(), budget, requestId).use(WorkspaceCommandFrame::read).also {
                            synchronized(attempt) { attempt.knownTerminal = true }
                        }
                    } catch (e: Exception) {
                        if (!service.isAlive()) throw CommandTransportException(CommandTransportFailure.SERVICE_DIED, e)
                        throw e
                    }
                    remaining()
                    if (!service.isAlive()) throw CommandTransportException(CommandTransportFailure.SERVICE_DIED)
                    result
                } finally { active.compareAndSet(attempt, null) }
            })
        } catch (e: RejectedExecutionException) {
            active.compareAndSet(attempt, null)
            throw CommandTransportException(CommandTransportFailure.CLOSED, e)
        }
        fun abort(reason: CommandTransportException, interrupted: Boolean = false): Nothing {
            val needsAcknowledgement = synchronized(attempt) {
                attempt.cancelled = true
                mutating && attempt.crossed && !attempt.knownTerminal
            }
            future.cancel(true)
            val acknowledgement = try { binding.invalidate(requestId) }
                catch (_: Exception) { CompletableFuture.completedFuture(false) }
            try {
                if (needsAcknowledgement && !awaitMutationAcknowledgement(acknowledgement))
                    throw UncertainWorkspaceMutation(reason)
                throw reason
            } finally {
                // A cancelled task may never enter the worker's finally.
                active.compareAndSet(attempt, null)
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
        try {
            return future.get(remaining(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            abort(CommandTransportException(CommandTransportFailure.CANCELLED, e), interrupted = true)
        } catch (e: TimeoutException) {
            abort(CommandTransportException(CommandTransportFailure.TIMEOUT, e))
        } catch (e: ExecutionException) {
            abort(e.cause as? CommandTransportException ?: CommandTransportException(CommandTransportFailure.EXECUTION_FAILED, e.cause))
        } catch (e: CommandTransportException) {
            abort(e)
        }
    }
    override fun execute(arguments: List<String>): WorkspaceCommandResult {
        val result = executeCommand(arguments)
        return WorkspaceCommandResult(result.exitCode, result.stdout + "\n" + result.stderr)
    }
    override fun close() {
        closed = true
        active.get()?.let { synchronized(it) { it.cancelled = true } }
        worker.shutdownNow()
        binding.invalidate(null)
    }
}

private fun awaitMutationAcknowledgement(acknowledgement: Future<Boolean>): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
    var interrupted = Thread.interrupted()
    try {
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return false
            try { return acknowledgement.get(remaining, TimeUnit.NANOSECONDS) }
            catch (_: InterruptedException) { interrupted = true }
            catch (_: Exception) { return false }
        }
    } finally { if (interrupted) Thread.currentThread().interrupt() }
}
internal object WorkspaceCommandWhitelist {
    fun validate(arguments: List<String>) {
        if (arguments == listOf("dumpsys", "activity", "activities")) return
        if (arguments.size == 8 && arguments.take(3) == listOf("am", "task", "resize")) {
            val numbers = arguments.drop(3).map { if (it.matches(Regex("[0-9]{1,10}"))) it.toIntOrNull() else null }
            if (numbers.all { it != null } && numbers[0]!! > 0 && numbers[3]!! > numbers[1]!! && numbers[4]!! > numbers[2]!!) return
        }
        throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
    }
}
internal object WorkspaceCommandFrame {
    const val STDOUT_LIMIT = 2_000_000
    const val STDERR_LIMIT = 64_000
    private const val MAGIC = 0x44575431
    fun write(output: OutputStream, result: ShizukuCommandResult) {
        val data = DataOutputStream(output)
        data.writeInt(MAGIC); data.writeInt(-1); data.writeInt(result.exitCode)
        writeText(data, result.stdout, STDOUT_LIMIT)
        writeText(data, result.stderr, STDERR_LIMIT)
        data.flush()
    }
    fun writeFailure(output: OutputStream, failure: CommandTransportFailure) {
        DataOutputStream(output).apply { writeInt(MAGIC); writeInt(failure.ordinal); flush() }
    }
    private fun writeText(data: DataOutputStream, text: String, limit: Int) {
        if (text.length > limit) throw CommandTransportException(CommandTransportFailure.OUTPUT_LIMIT)
        val bytes = text.toByteArray(Charsets.UTF_8)
        data.writeInt(bytes.size); data.write(bytes)
    }
    fun read(input: InputStream): ShizukuCommandResult = try {
        val data = DataInputStream(input)
        if (data.readInt() != MAGIC) throw IOException("Invalid command frame")
        val code = data.readInt()
        if (code != -1) throw CommandTransportException(CommandTransportFailure.entries.getOrNull(code)
            ?: CommandTransportFailure.MALFORMED_RESULT)
        ShizukuCommandResult(data.readInt(), readText(data, STDOUT_LIMIT), readText(data, STDERR_LIMIT))
    } catch (e: CommandTransportException) { throw e }
      catch (e: IOException) { throw CommandTransportException(CommandTransportFailure.MALFORMED_RESULT, e) }
    private fun readText(data: DataInputStream, limit: Int): String {
        val size = data.readInt()
        if (size < 0 || size > limit * 3) throw IOException("Invalid output size")
        val bytes = ByteArray(size)
        data.readFully(bytes)
        val text = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        if (text.length > limit) throw CommandTransportException(CommandTransportFailure.OUTPUT_LIMIT)
        return text
    }
}
