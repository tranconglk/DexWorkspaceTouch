package com.trancong.dexworkspacetouch.diagnostics.embedded

import android.os.Process
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.UUID

/** Process-local facade. Không giữ Context, gate, runtime hoặc consumer. */
object EmbeddedEvidence {
    @Volatile private var appRecorder: EvidenceRecorder? = null
    @Volatile private var remoteRecorder: EvidenceRecorder? = null
    private var appWriter: EvidenceWriter? = null
    private var remoteWriter: EvidenceWriter? = null

    inline fun observe(block: () -> Unit) { try { block() } catch (_: Throwable) { /* Chẩn đoán không điều khiển execution. */ } }
    fun installApp(recorder: EvidenceRecorder?) { appRecorder = recorder }
    fun installRemote(recorder: EvidenceRecorder?) { remoteRecorder = recorder }
    fun initializeApp(directory: File) = observe {
        if (appRecorder == null) {
            val recorder = EvidenceRecorder("app", Process.myPid(), Process.myUid(), UUID.randomUUID().toString())
            appRecorder = recorder
            appWriter = EvidenceWriter(recorder, EvidenceFileStore(directory, recorder.limits)).also { it.start() }
        }
    }
    private fun initializeRemote() {
        if (remoteRecorder != null) return
        synchronized(this) {
            if (remoteRecorder == null) {
                val recorder = EvidenceRecorder("remote", Process.myPid(), Process.myUid(), UUID.randomUUID().toString())
                remoteRecorder = recorder
                remoteWriter = EvidenceWriter(recorder,
                    EvidenceFileStore(File("/data/local/tmp/dwt-vdm-012-evidence"), recorder.limits)).also { it.start() }
            }
        }
    }
    fun app(event: String, sid: String? = null, cell: String? = null, graph: String? = null,
        fields: Map<String, String> = emptyMap()) = observe { appRecorder?.emit(event, sid, cell, graph, fields) }
    fun remote(event: String, sid: String? = null, fields: Map<String, String> = emptyMap()) = observe {
        initializeRemote(); remoteRecorder?.emit(event, sid, fields = fields)
    }
    inline fun <T> remoteStep(sid: String, stage: String, block: () -> T): T {
        remote("step.begin", sid, mapOf("stage" to stage))
        return try {
            block().also { remote("step.end", sid, mapOf("stage" to stage)) }
        } catch (error: Throwable) {
            observe { remote("step.failure", sid, errorFields(error) + ("stage" to stage)) }
            throw error
        }
    }
    fun errorFields(error: Throwable): Map<String, String> {
        var cause = error
        repeat(8) { if (cause is InvocationTargetException) cause = (cause as InvocationTargetException).targetException ?: cause }
        return buildMap {
            put("exception_type", cause.javaClass.name)
            cause.message?.let { put("message", it) }
            if (cause.javaClass.name == "android.os.ServiceSpecificException") {
                runCatching { cause.javaClass.getField("errorCode").getInt(cause) }.getOrNull()
                    ?.let { put("exception_code", it.toString()) }
            }
        }
    }
}
