package com.trancong.dexworkspacetouch.workspace.close

import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine

class CloseRpcLane {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "dwt-close-rpc").apply { isDaemon = true } }
    private val lock = Any()
    private var busy = false
    private var cleanup: (() -> Unit)? = null
    val quiescent: Boolean get() = synchronized(lock) { !busy }

    suspend fun <T> call(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        synchronized(lock) {
            if (busy) { continuation.resumeWith(Result.failure(CloseBackendException("EXECUTOR_IN_FLIGHT"))); return@suspendCancellableCoroutine }
            busy = true
        }
        executor.execute {
            val result = runCatching { if (!continuation.isActive) throw CloseBackendException("CALL_CANCELLED"); block() }
            finish()
            continuation.resumeWith(result)
        }
    }
    fun afterQuiescent(block: () -> Unit) {
        val start = synchronized(lock) {
            val old = cleanup
            cleanup = { old?.invoke(); block() }
            if (busy) false else { busy = true; true }
        }
        if (start) executor.execute(::finish)
    }
    private fun finish() {
        while (true) {
            val next = synchronized(lock) { cleanup.also { cleanup = null; if (it == null) busy = false } } ?: return
            runCatching(next)
        }
    }
}
