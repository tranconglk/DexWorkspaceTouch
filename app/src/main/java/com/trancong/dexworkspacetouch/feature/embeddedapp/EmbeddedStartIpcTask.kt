package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService

internal data class StartCompletion(
    val operation: Long,
    val success: Boolean,
    val displayId: Int,
    val error: String?,
)

/** Only Binder/value/transport fields; never loads a consumer across synchronous Start IPC. */
internal class StartIpcTask(
    private val service: IEmbeddedAppService,
    private val sessionId: String,
    private val packageName: String,
    private val componentName: String,
    private val width: Int,
    private val height: Int,
    private val densityDpi: Int,
    private val surface: Surface,
    val operation: Long,
    private val destination: DetachableMailbox<StartCompletion>,
) : Runnable {
    override fun run() {
        val completion = try {
            val result = service.startSession(sessionId, surface, packageName, componentName, width, height, densityDpi)
            val success = result.getBoolean("success")
            StartCompletion(operation, success, result.getInt("displayId"),
                if (success) null else result.getString("exception")?.take(512) ?: "Session start failed")
        } catch (_: Exception) {
            StartCompletion(operation, false, -1, "START_IPC_FAILED")
        }
        destination.offer(completion)
    }
}
