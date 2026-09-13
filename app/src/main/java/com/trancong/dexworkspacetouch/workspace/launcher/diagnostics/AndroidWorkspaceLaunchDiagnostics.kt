package com.trancong.dexworkspacetouch.workspace.launcher.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import com.trancong.dexworkspacetouch.workspace.launcher.model.AppLaunchTarget
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AndroidWorkspaceLaunchDiagnostics private constructor(
    private val storage: WorkspaceLaunchDiagnosticStorage,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val sessionIdFactory: () -> String,
) : WorkspaceLaunchDiagnostics {
    private val sessions = ConcurrentHashMap<String, WorkspaceLaunchDiagnosticSession>()

    override fun begin(request: WorkspaceLaunchRequest): String? = safely {
        val id = sessionIdFactory()
        sessions[id] = WorkspaceLaunchDiagnosticSession(
            id, clock(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(),
            WorkspaceLaunchDiagnosticDevice(Build.MANUFACTURER.orEmpty(), Build.MODEL.orEmpty(), Build.VERSION.SDK_INT),
            request.workspaceId, request.workspaceName, request.targets.size,
        )
        id
    }

    override fun record(
        context: WorkspaceLaunchDiagnosticContext,
        target: AppLaunchTarget,
        activityInfo: WorkspaceLaunchActivityInfo?,
        snapshot: DisplayWorkAreaSnapshot?,
        beforeClampBounds: DiagnosticRect?,
        requestedPixelBounds: PixelBounds?,
        marginPx: Int?,
        intentFlags: Int,
        launchDisplayId: Int?,
        launchStartedAtEpochMillis: Long?,
        launchCompletedAtEpochMillis: Long?,
        result: String,
    ) {
        safely {
            sessions.computeIfPresent(context.sessionId) { _, session ->
                val entry = WorkspaceLaunchDiagnosticApp(
                    context.sequenceIndex, target.identity.packageName, target.identity.activityName,
                    activityInfo, target.bounds, snapshot?.toDiagnosticDisplay(), beforeClampBounds,
                    requestedPixelBounds?.toDiagnosticRect(), marginPx, intentFlags, launchDisplayId,
                    launchStartedAtEpochMillis, launchCompletedAtEpochMillis, result,
                )
                session.copy(apps = (session.apps.filterNot { it.sequenceIndex == context.sequenceIndex } + entry)
                    .sortedBy { it.sequenceIndex })
            }
            persist(context.sessionId)
        }
    }

    override fun finish(sessionId: String) {
        safely { persist(sessionId, removeAfterWrite = true) }
    }

    fun latestReport(): String? = safely { storage.latest() }
    fun clear(): Boolean = safely { storage.clear(); true } ?: false
    fun hasReports(): Boolean = safely { storage.hasReports() } ?: false

    private fun persist(sessionId: String, removeAfterWrite: Boolean = false) {
        val session = sessions[sessionId] ?: return
        val content = formatWorkspaceLaunchDiagnostic(session)
        scope.launch {
            safely { storage.write(session.sessionId, session.timestampEpochMillis, content) }
            if (removeAfterWrite) sessions.remove(sessionId)
        }
    }

    private inline fun <T> safely(block: () -> T): T? = try {
        block()
    } catch (error: Exception) {
        Log.w(TAG, "Workspace launch diagnostics unavailable: ${error.javaClass.simpleName}")
        null
    }

    companion object {
        private const val TAG = "WorkspaceLaunchDiag"
        fun create(context: Context): AndroidWorkspaceLaunchDiagnostics = AndroidWorkspaceLaunchDiagnostics(
            FileWorkspaceLaunchDiagnosticStorage(File(context.filesDir, "workspace-launch-diagnostics")),
            CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1)), System::currentTimeMillis,
            { UUID.randomUUID().toString() },
        )
    }
}

interface WorkspaceLaunchDiagnosticStorage {
    fun write(sessionId: String, timestampEpochMillis: Long, content: String)
    fun latest(): String?
    fun hasReports(): Boolean
    fun clear()
}

class FileWorkspaceLaunchDiagnosticStorage(private val directory: File) : WorkspaceLaunchDiagnosticStorage {
    override fun write(sessionId: String, timestampEpochMillis: Long, content: String) {
        directory.mkdirs()
        val target = File(directory, "$timestampEpochMillis-$sessionId.json")
        val temporary = File(directory, ".${target.name}.tmp")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            if (target.exists() && !target.delete()) error("Cannot replace diagnostic report")
            if (!temporary.renameTo(target)) error("Cannot finalize diagnostic report")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        trim()
    }

    override fun latest(): String? = filesNewestFirst().firstOrNull()?.readText(Charsets.UTF_8)
    override fun hasReports(): Boolean = filesNewestFirst().isNotEmpty()
    override fun clear() { directory.listFiles()?.forEach { if (it.isFile) it.delete() } }

    private fun trim() {
        val keep = retainedDiagnosticFilesNewestFirst(filesNewestFirst().map(File::getName)).toSet()
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") && it.name !in keep }
            ?.forEach(File::delete)
    }

    private fun filesNewestFirst(): List<File> = directory.listFiles()
        ?.filter { it.isFile && !it.name.endsWith(".tmp") }
        ?.sortedByDescending(File::getName)
        .orEmpty()
}
