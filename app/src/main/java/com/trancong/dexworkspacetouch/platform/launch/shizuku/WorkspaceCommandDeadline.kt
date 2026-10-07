package com.trancong.dexworkspacetouch.platform.launch.shizuku

/** Same-device monotonic absolute deadline; time spent queued in Binder belongs to the command budget. */
internal fun workspaceDeadlineRemaining(deadlineMs: Long, nowMs: Long): Long {
    if (deadlineMs < 0 || nowMs < 0) throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
    if (deadlineMs <= nowMs) throw CommandTransportException(CommandTransportFailure.TIMEOUT)
    val remaining = deadlineMs - nowMs
    if (remaining > 8000) throw CommandTransportException(CommandTransportFailure.INVALID_COMMAND)
    return remaining
}
