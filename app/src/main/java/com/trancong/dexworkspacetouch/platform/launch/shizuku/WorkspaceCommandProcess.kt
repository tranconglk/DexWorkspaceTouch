package com.trancong.dexworkspacetouch.platform.launch.shizuku

import java.io.InputStream
import java.util.concurrent.*

/** Runs in the shell UserService, never against Shizuku's deprecated remote Process API. */
internal fun awaitWorkspaceCommandProcess(process: Process, timeoutMs: Long): ShizukuCommandResult {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    val readers = Executors.newFixedThreadPool(3) { Thread(it, "DWT.CommandDrain").apply { isDaemon = true } }
    fun <T> await(future: Future<T>): T {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0) throw TimeoutException()
        return future.get(remaining, TimeUnit.NANOSECONDS)
    }
    try {
        if (Thread.currentThread().isInterrupted) throw InterruptedException()
        process.outputStream.close()
        val stdout = readers.submit(Callable { readWorkspaceOutput(process.inputStream, WorkspaceCommandFrame.STDOUT_LIMIT) })
        val stderr = readers.submit(Callable { readWorkspaceOutput(process.errorStream, WorkspaceCommandFrame.STDERR_LIMIT) })
        val exit = await(readers.submit(Callable { process.waitFor() }))
        return ShizukuCommandResult(exit, await(stdout), await(stderr))
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        throw CommandTransportException(CommandTransportFailure.CANCELLED, e)
    } catch (e: TimeoutException) {
        throw CommandTransportException(CommandTransportFailure.TIMEOUT, e)
    } catch (e: ExecutionException) {
        throw (e.cause as? CommandTransportException ?: CommandTransportException(CommandTransportFailure.EXECUTION_FAILED, e.cause))
    } finally {
        process.destroyForcibly()
        readers.shutdownNow()
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
    }
}
private fun readWorkspaceOutput(stream: InputStream, limit: Int): String = stream.bufferedReader().use { reader ->
    val text = StringBuilder()
    val buffer = CharArray(4096)
    while (true) {
        val count = reader.read(buffer)
        if (count < 0) break
        if (text.length + count > limit) throw CommandTransportException(CommandTransportFailure.OUTPUT_LIMIT)
        text.append(buffer, 0, count)
    }
    text.toString()
}
