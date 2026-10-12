package com.trancong.dexworkspacetouch.workspace.close

import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import java.util.UUID

const val DWT_PROTECTED_PACKAGE = "com.trancong.dexworkspacetouch"

data class PackageCloseRequest(val operationId: String, val packagesRequested: List<String>, val userId: Int = 0) {
    fun snapshot(): PackageCloseRequest {
        require(UUID.fromString(operationId).toString() == operationId) { "INVALID_OPERATION_ID" }
        require(packagesRequested.all { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) }) { "INVALID_PACKAGE" }
        return copy(packagesRequested = packagesRequested.distinct().toList())
    }

    companion object {
        fun fromWorkspace(workspace: Workspace) = PackageCloseRequest(
            UUID.randomUUID().toString(), workspace.canvas.cells.mapNotNull { it.app?.packageName }.distinct(),
        )
    }
}

enum class PackageCloseStatus { COMPLETE, NO_OP, PARTIAL, BACKEND_UNAVAILABLE, UNSUPPORTED }
enum class CloseObservation { VERIFIED_EMPTY, VERIFIED_PRESENT, UNKNOWN }
enum class CloseDispatch { NOT_SENT, DISPATCH_INTENT, REPLY_RECEIVED }
enum class CloseRemoveResult { NOT_CALLED, ALREADY_GONE, REMOVE_TRUE, REMOVE_FALSE, IDENTITY_CHANGED, UNSUPPORTED_TASK, ERROR, UNKNOWN }

data class CloseTask(
    val taskId: Int, val userId: Int, val displayId: Int, val parentOrRootId: Int,
    val baseActivity: String?, val topActivity: String?, val leaf: Boolean,
) {
    val basePackage: String? get() = baseActivity?.substringBefore('/')
    val topPackage: String? get() = topActivity?.substringBefore('/')
    fun sameIdentity(other: CloseTask) = taskId == other.taskId && userId == other.userId &&
        baseActivity == other.baseActivity && topActivity == other.topActivity && leaf == other.leaf
}

data class CloseSnapshot(
    val tasks: List<CloseTask>, val complete: Boolean, val sequence: Int, val observedAt: Long,
    val adapter: String, val build: String, val reason: String? = null,
)
data class CloseTaskResult(
    val expected: CloseTask, val discoveryRound: Int,
    val dispatch: CloseDispatch = CloseDispatch.NOT_SENT,
    val result: CloseRemoveResult = CloseRemoveResult.NOT_CALLED,
    val identityAtRemove: CloseTask? = null,
    val finalObservation: CloseObservation = CloseObservation.UNKNOWN,
)
data class CloseRemaining(
    val state: CloseObservation, val observedTasks: List<CloseTask>,
    val snapshotSequence: Int?, val observedAt: Long?, val reason: String? = null,
)
data class CloseLimits(val batches: Int = 3, val attempts: Int = 128, val deadlineMs: Long = 10_000, val ipcMs: Long = 2_000, val connectMs: Long = 5_000) {
    init { require(batches in 1..3 && attempts in 1..128 && deadlineMs in 1..10_000 && ipcMs in 1..2_000 && connectMs in 1..5_000) }
}
data class PackageCloseReport(
    val request: PackageCloseRequest, val status: PackageCloseStatus, val terminalReason: String?,
    val tasksBefore: List<CloseTask>, val perTaskResults: List<CloseTaskResult>,
    val remainingByPackage: Map<String, CloseRemaining>,
    val enumerationBefore: CloseSnapshot?, val verification: List<CloseSnapshot>,
    val startedAt: Long, val finishedAt: Long, val limits: CloseLimits,
    val persistenceFailed: Boolean = false,
)
data class CloseOperationRecord(
    val request: PackageCloseRequest, val revision: Int = 0,
    val tasksBefore: List<CloseTask> = emptyList(), val results: List<CloseTaskResult> = emptyList(),
    val before: CloseSnapshot? = null, val lastSnapshot: CloseSnapshot? = null,
    val startedAt: Long, val limits: CloseLimits = CloseLimits(), val terminal: PackageCloseReport? = null,
)

interface CloseOperationStore {
    fun read(operationId: String): CloseOperationRecord?
    fun pending(): List<CloseOperationRecord>
    /** Must durably persist; existing revisions must advance by exactly one. */
    fun write(record: CloseOperationRecord)
}
interface CloseTaskBackend {
    /** False while a timed-out transaction may still be running. */
    val quiescent: Boolean
    suspend fun checkAvailable()
    suspend fun open(request: PackageCloseRequest, deadline: Long)
    suspend fun enumerate(): CloseSnapshot
    suspend fun removeVerified(expected: CloseTask): CloseTaskResult
    /** Local invalidation is synchronous; never wait for a stuck remote call here. */
    fun invalidate()
}
interface PackageCloseBackend { suspend fun closePackages(request: PackageCloseRequest): PackageCloseReport }
class CloseBackendException(val reason: String) : Exception(reason)
