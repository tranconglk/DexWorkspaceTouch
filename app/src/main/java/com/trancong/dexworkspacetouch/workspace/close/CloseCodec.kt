package com.trancong.dexworkspacetouch.workspace.close

import org.json.JSONArray
import org.json.JSONObject

/** Shared wire/journal schema; missing mandatory evidence is a decoding failure. */
object CloseCodec {
    private fun obj(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { put(it.first, it.second ?: JSONObject.NULL) } }
    private fun <T> list(array: JSONArray, read: (JSONObject) -> T) = (0 until array.length()).map { read(array.getJSONObject(it)) }
    private fun <T> array(values: List<T>, encode: (T) -> JSONObject) = JSONArray().apply { values.forEach { put(encode(it)) } }
    private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
    private fun <T> JSONObject.optional(key: String, decode: (JSONObject) -> T): T? = if (isNull(key)) null else decode(getJSONObject(key))

    fun request(r: PackageCloseRequest) = obj("operationId" to r.operationId, "packagesRequested" to JSONArray(r.packagesRequested), "userId" to r.userId)
    fun request(j: JSONObject) = PackageCloseRequest(j.getString("operationId"), j.getJSONArray("packagesRequested").let { a ->
        (0 until a.length()).map(a::getString) }, j.getInt("userId")).snapshot()
    fun task(t: CloseTask) = obj("taskId" to t.taskId, "userId" to t.userId, "displayId" to t.displayId,
        "parentOrRootId" to t.parentOrRootId, "baseActivity" to t.baseActivity, "topActivity" to t.topActivity, "leaf" to t.leaf)
    fun task(j: JSONObject) = CloseTask(j.getInt("taskId"), j.getInt("userId"), j.getInt("displayId"), j.getInt("parentOrRootId"),
        j.stringOrNull("baseActivity"), j.stringOrNull("topActivity"), j.getBoolean("leaf"))
    fun snapshot(s: CloseSnapshot) = obj("tasks" to array(s.tasks, ::task), "complete" to s.complete, "sequence" to s.sequence,
        "observedAt" to s.observedAt, "adapter" to s.adapter, "build" to s.build, "reason" to s.reason)
    fun snapshot(j: JSONObject) = CloseSnapshot(list(j.getJSONArray("tasks"), ::task), j.getBoolean("complete"), j.getInt("sequence"),
        j.getLong("observedAt"), j.getString("adapter"), j.getString("build"), j.stringOrNull("reason"))
    fun result(r: CloseTaskResult) = obj("expected" to task(r.expected), "discoveryRound" to r.discoveryRound,
        "dispatch" to r.dispatch.name, "result" to r.result.name, "identityAtRemove" to r.identityAtRemove?.let(::task), "finalObservation" to r.finalObservation.name)
    fun result(j: JSONObject) = CloseTaskResult(task(j.getJSONObject("expected")), j.getInt("discoveryRound"),
        CloseDispatch.valueOf(j.getString("dispatch")), CloseRemoveResult.valueOf(j.getString("result")), j.optional("identityAtRemove", ::task),
        CloseObservation.valueOf(j.getString("finalObservation")))
    private fun limits(l: CloseLimits) = obj("batches" to l.batches, "attempts" to l.attempts, "deadlineMs" to l.deadlineMs, "ipcMs" to l.ipcMs, "connectMs" to l.connectMs)
    private fun limits(j: JSONObject) = CloseLimits(j.getInt("batches"), j.getInt("attempts"), j.getLong("deadlineMs"), j.getLong("ipcMs"), j.optLong("connectMs", 5_000))
    private fun remaining(r: CloseRemaining) = obj("state" to r.state.name, "observedTasks" to array(r.observedTasks, ::task),
        "snapshotSequence" to r.snapshotSequence, "observedAt" to r.observedAt, "reason" to r.reason)
    private fun remaining(j: JSONObject) = CloseRemaining(CloseObservation.valueOf(j.getString("state")), list(j.getJSONArray("observedTasks"), ::task),
        if (j.isNull("snapshotSequence")) null else j.getInt("snapshotSequence"), if (j.isNull("observedAt")) null else j.getLong("observedAt"), j.stringOrNull("reason"))
    fun report(r: PackageCloseReport) = obj("request" to request(r.request), "status" to r.status.name, "terminalReason" to r.terminalReason,
        "tasksBefore" to array(r.tasksBefore, ::task), "perTaskResults" to array(r.perTaskResults, ::result),
        "remainingByPackage" to JSONObject().apply { r.remainingByPackage.forEach { (pkg, value) -> put(pkg, remaining(value)) } },
        "enumerationBefore" to r.enumerationBefore?.let(::snapshot), "verification" to array(r.verification, ::snapshot),
        "startedAt" to r.startedAt, "finishedAt" to r.finishedAt, "limits" to limits(r.limits), "persistenceFailed" to r.persistenceFailed)
    fun report(j: JSONObject): PackageCloseReport {
        val remaining = j.getJSONObject("remainingByPackage")
        return PackageCloseReport(request(j.getJSONObject("request")), PackageCloseStatus.valueOf(j.getString("status")), j.stringOrNull("terminalReason"),
            list(j.getJSONArray("tasksBefore"), ::task), list(j.getJSONArray("perTaskResults"), ::result),
            remaining.keys().asSequence().associateWith { remaining(remaining.getJSONObject(it)) },
            j.optional("enumerationBefore", ::snapshot), list(j.getJSONArray("verification"), ::snapshot),
            j.getLong("startedAt"), j.getLong("finishedAt"), limits(j.getJSONObject("limits")), j.getBoolean("persistenceFailed"))
    }
    fun record(r: CloseOperationRecord) = obj("schemaVersion" to 1, "request" to request(r.request), "revision" to r.revision,
        "tasksBefore" to array(r.tasksBefore, ::task), "results" to array(r.results, ::result), "before" to r.before?.let(::snapshot),
        "lastSnapshot" to r.lastSnapshot?.let(::snapshot), "startedAt" to r.startedAt, "limits" to limits(r.limits), "terminal" to r.terminal?.let(::report))
    fun record(j: JSONObject): CloseOperationRecord {
        require(j.getInt("schemaVersion") == 1) { "UNSUPPORTED_JOURNAL_SCHEMA" }
        return CloseOperationRecord(request(j.getJSONObject("request")), j.getInt("revision"), list(j.getJSONArray("tasksBefore"), ::task),
            list(j.getJSONArray("results"), ::result), j.optional("before", ::snapshot), j.optional("lastSnapshot", ::snapshot),
            j.getLong("startedAt"), limits(j.getJSONObject("limits")), j.optional("terminal", ::report))
    }
}
