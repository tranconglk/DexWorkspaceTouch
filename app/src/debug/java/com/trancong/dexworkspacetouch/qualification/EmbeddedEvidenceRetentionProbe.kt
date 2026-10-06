package com.trancong.dexworkspacetouch.qualification

import android.os.Process
import com.trancong.dexworkspacetouch.diagnostics.embedded.EmbeddedEvidence
import java.io.File
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.EmbeddedAppVdm
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import com.trancong.dexworkspacetouch.feature.embeddedapp.StartIpcTask
import com.trancong.dexworkspacetouch.feature.embeddedapp.StartCompletion
import com.trancong.dexworkspacetouch.feature.embeddedapp.DetachableMailbox
import com.trancong.dexworkspacetouch.diagnostics.embedded.EvidenceRecorder
import com.trancong.dexworkspacetouch.diagnostics.embedded.EvidenceFileStore
import android.os.Bundle
import java.lang.reflect.Proxy
import java.util.UUID
import java.util.concurrent.Executor

/** Chỉ ghi chẩn đoán dưới shell app_process; không tạo/bind bất kỳ session nào. */
object EmbeddedEvidenceRetentionProbe {
    @JvmStatic fun main(args: Array<String>) {
        check(Process.myUid() == 2000)
        val sid = "012-retention-plumbing"
        EmbeddedEvidence.remote("plumbing.begin", sid, mapOf("stage" to "diagnostic_plumbing", "attempted" to "false"))
        EmbeddedEvidence.remote("plumbing.failure", sid, EmbeddedEvidence.errorFields(
            IllegalStateException("Received Surface became invalid")))
        EmbeddedEvidence.remote("plumbing.end", sid, mapOf("success" to "true", "returned" to "false"))
        // Surface rỗng bị guard từ chối trước association/device/input/task allocation.
        val vdm = EmbeddedAppVdm("DWT-012-plumbing", sid)
        val surface = Surface::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        try {
            verifyRawReply(surface)
            val result = vdm.create(surface, EmbeddedAppTarget("diagnostic.fixture", "diagnostic.fixture.Main",
                EmbeddedAppGeometry(100, 100, 160)), 1)
            check(!result.getBoolean("success"))
            check(vdm.cleanup().getBoolean("success"))
            vdm.finishCleanupAfterAssociation()
        } finally { surface.release() }
        Thread.sleep(1000)
        val segments = File("/data/local/tmp/dwt-vdm-012-evidence").listFiles().orEmpty().filter { it.extension == "jsonl" }
        check(segments.isNotEmpty())
        check(segments.size <= 4 && segments.all { it.length() <= 128 * 1024 })
        println("012_NON_OWNING_RETENTION uid=${Process.myUid()} pid=${Process.myPid()} segments=${segments.size} bytes=${segments.sumOf { it.length() }}")
    }

    private fun verifyRawReply(surface: Surface) {
        val recorder = EvidenceRecorder("app", Process.myPid(), Process.myUid(), UUID.randomUUID().toString())
        EmbeddedEvidence.installApp(recorder)
        val raw = "java.lang.IllegalStateException: " + "UNSAFE_PAYLOAD".repeat(100)
        val reply = Bundle().apply { putBoolean("success", false); putString("exception", raw); putString("failureCode", "START_FAILED") }
        val service = Proxy.newProxyInstance(IEmbeddedAppService::class.java.classLoader,
            arrayOf(IEmbeddedAppService::class.java)) { _, method, _ ->
                if (method.name == "startSession") reply else null
            } as IEmbeddedAppService
        val completions = mutableListOf<StartCompletion>()
        try {
            StartIpcTask(service, "012-ipc-plumbing", "diagnostic.fixture", "diagnostic.fixture.Main", 100, 100, 160,
                surface, 17, DetachableMailbox(Executor { it.run() }) { completions += it }).run()
            check(completions.single().error?.length == 512)
            val records = recorder.drain()
            val captured = records.single { it.event == "ipc.raw_reply" }
            check(captured.fields["raw_message_chars"] == raw.length.toString())
            check(captured.redactedFields > 0 && !captured.toJson().contains("UNSAFE_PAYLOAD"))
            val store = EvidenceFileStore(File("/data/local/tmp/dwt-vdm-012-app-plumbing"))
            check(records.all { store.append(it) })
            println("012_RAW_REPLY_CAPTURE raw_chars=${raw.length} projected_chars=512 unsafe_message_omitted=true")
        } finally { EmbeddedEvidence.installApp(null) }
    }
}
