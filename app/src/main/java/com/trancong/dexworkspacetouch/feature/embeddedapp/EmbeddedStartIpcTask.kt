package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.view.Surface
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence
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
        EmbeddedEvidence.observe { EmbeddedEvidence.app("ipc.begin", sessionId, fields = mapOf("ipc_operation" to operation.toString(),
            "surface_identity" to System.identityHashCode(surface).toString(), "surface_valid" to surface.isValid.toString())) }
        val completion = try {
            val result = service.startSession(sessionId, surface, packageName, componentName, width, height, densityDpi)
            val success = result.getBoolean("success")
            EmbeddedEvidence.observe {
                val fields = linkedMapOf("ipc_operation" to operation.toString(), "success" to success.toString())
                result.getString("exception")?.let { raw ->
                    fields["raw_message_chars"] = raw.length.toString()
                    val split = raw.indexOf(": ")
                    if (split > 0) { fields["exception_type"] = raw.substring(0, split); fields["message"] = raw.substring(split + 2) }
                    else fields["message"] = raw
                }
                result.getString("failureCode")?.let { fields["failure_code"] = it }
                if (result.containsKey("remoteUid")) fields["remote_uid"] = result.getInt("remoteUid").toString()
                for ((key, field) in mapOf("displayId" to "display_id", "deviceId" to "device_id",
                    "associationId" to "association_id", "inputDeviceId" to "input_device_id", "launchResult" to "launch_result")) {
                    if (result.containsKey(key)) fields[field] = result.getInt(key).toString()
                }
                EmbeddedEvidence.app("ipc.raw_reply", sessionId, fields = fields)
            }
            StartCompletion(operation, success, result.getInt("displayId"),
                if (success) null else result.getString("exception")?.take(512) ?: "Session start failed")
        } catch (error: Exception) {
            EmbeddedEvidence.observe { EmbeddedEvidence.app("ipc.failure", sessionId,
                fields = EmbeddedEvidence.errorFields(error) + ("ipc_operation" to operation.toString())) }
            StartCompletion(operation, false, -1, "START_IPC_FAILED")
        }
        val admitted = destination.offer(completion)
        EmbeddedEvidence.app("ipc.mailbox", sessionId, fields = mapOf("ipc_operation" to operation.toString(), "admitted" to admitted.toString()))
    }
}
