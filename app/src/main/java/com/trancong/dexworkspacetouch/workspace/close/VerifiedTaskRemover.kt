package com.trancong.dexworkspacetouch.workspace.close

interface CloseTaskControl {
    fun enumerate(): CloseSnapshot
    fun removeTask(taskId: Int): Boolean
}
class VerifiedTaskRemover(private val request: PackageCloseRequest, private val control: CloseTaskControl, private val gate: () -> Boolean) {
    fun remove(expected: CloseTask): CloseTaskResult {
        fun reply(result: CloseRemoveResult, current: CloseTask? = null) =
            CloseTaskResult(expected, 0, CloseDispatch.REPLY_RECEIVED, result, current)
        if (CloseTaskPolicy.preflight(request) != null || !CloseTaskPolicy.safe(request, expected))
            return reply(CloseRemoveResult.UNSUPPORTED_TASK)
        if (!gate()) throw CloseBackendException("BACKEND_LOST")
        val snapshot = control.enumerate()
        CloseTaskPolicy.coverageProblem(snapshot)?.let { throw CloseBackendException(it) }
        val current = snapshot.tasks.firstOrNull { it.userId == expected.userId && it.taskId == expected.taskId }
            ?: return reply(CloseRemoveResult.ALREADY_GONE)
        if (!expected.sameIdentity(current)) return reply(CloseRemoveResult.IDENTITY_CHANGED, current)
        if (!CloseTaskPolicy.safe(request, current)) return reply(CloseRemoveResult.UNSUPPORTED_TASK, current)
        if (!gate()) throw CloseBackendException("BACKEND_LOST")
        // The synchronous permission gate can yield; do not remove from the identity observed before it.
        val finalSnapshot = control.enumerate()
        CloseTaskPolicy.coverageProblem(finalSnapshot)?.let { throw CloseBackendException(it) }
        val final = finalSnapshot.tasks.firstOrNull { it.userId == expected.userId && it.taskId == expected.taskId }
            ?: return reply(CloseRemoveResult.ALREADY_GONE)
        if (!expected.sameIdentity(final)) return reply(CloseRemoveResult.IDENTITY_CHANGED, final)
        if (!CloseTaskPolicy.safe(request, final)) return reply(CloseRemoveResult.UNSUPPORTED_TASK, final)
        return try { reply(if (control.removeTask(final.taskId)) CloseRemoveResult.REMOVE_TRUE else CloseRemoveResult.REMOVE_FALSE, final) }
        catch (e: Exception) { reply(CloseRemoveResult.ERROR, final) }
    }
}
