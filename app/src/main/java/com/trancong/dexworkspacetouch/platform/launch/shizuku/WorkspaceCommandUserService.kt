package com.trancong.dexworkspacetouch.platform.launch.shizuku

import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import kotlin.system.exitProcess

/** Dedicated, non-daemon shell service; no Embedded lease/session/ownership dependencies. */
class WorkspaceCommandUserService : IWorkspaceCommandService.Stub() {
    private val dispatcher = WorkspaceCommandDispatcher()
    override fun getUid() = Process.myUid()

    override fun execute(arguments: Array<out String>, deadlineElapsedRealtimeMs: Long, requestId: String): ParcelFileDescriptor {
        if (Process.myUid() != 2000) throw SecurityException(CommandTransportFailure.UID_UNSUPPORTED.name)
        WorkspaceCommandWhitelist.validate(arguments.toList())
        val timeoutMs = workspaceDeadlineRemaining(deadlineElapsedRealtimeMs, SystemClock.elapsedRealtime())
        val pipe = ParcelFileDescriptor.createPipe()
        try {
            dispatcher.execute(arguments.toList(), timeoutMs, requestId) { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]) }
        } catch (e: Exception) {
            pipe.forEach { it.close() }
            throw e
        }
        return pipe[0]
    }
    override fun cancel(requestId: String) = dispatcher.cancel(requestId)
    override fun destroy() {
        dispatcher.close()
        exitProcess(0)
    }
}
