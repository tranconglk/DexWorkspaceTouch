package com.trancong.dexworkspacetouch.workspace.close.remote

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import com.trancong.dexworkspacetouch.workspace.close.*
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

class PackageCloseUserService(context: Context) : IPackageCloseService.Stub() {
    private val callerUid = context.packageManager.getApplicationInfo(DWT_PROTECTED_PACKAGE, 0).uid
    private val homePackage = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
        PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    private val gateLane = Executors.newSingleThreadExecutor { Thread(it, "dwt-close-gate").apply { isDaemon = true } }
    private val gateBusy = AtomicBoolean(false)
    @Volatile private var active = false
    private var request: PackageCloseRequest? = null
    private var deadline = 0L
    private var gate: ICloseSessionGate? = null
    private var control: CloseTaskControl? = null
    private val death = IBinder.DeathRecipient { active = false }

    private fun authorizeCaller() { check(Process.myUid() == 2000 && Binder.getCallingUid() == callerUid && callerUid < 100_000) { "UNAUTHORIZED_CALLER" } }
    override fun getUid(): Int { authorizeCaller(); return Process.myUid() }
    private fun envelope(block: () -> JSONObject): String = try {
        authorizeCaller()
        JSONObject().put("success", true).put("evidence", block()).toString()
    } catch (e: Exception) {
        JSONObject().put("success", false).put("reason", (e as? CloseBackendException)?.reason ?: "UNSUPPORTED_ADAPTER_OR_EVIDENCE").toString()
    }
    @Synchronized override fun openSession(requestJson: String, deadline: Long, gate: ICloseSessionGate): String = envelope {
        check(request == null) { "SESSION_ALREADY_OPEN" }
        val incoming = CloseCodec.request(JSONObject(requestJson))
        CloseTaskPolicy.preflight(incoming)?.let { throw CloseBackendException(it) }
        if (homePackage == null) throw CloseBackendException("UNSUPPORTED_HOME_IDENTITY")
        if (homePackage in incoming.packagesRequested) throw CloseBackendException("PROTECTED_PACKAGE")
        if (deadline <= SystemClock.elapsedRealtime() || deadline - SystemClock.elapsedRealtime() > 10_000) throw CloseBackendException("INVALID_DEADLINE")
        val profile = CloseAdapterProfile.select(Build.VERSION.SDK_INT, Build.FINGERPRINT)
        val platform = when (profile) {
            CloseAdapterProfile.NOTE9_API29 -> Api29CloseTaskControl()
            CloseAdapterProfile.S23_API36 -> Api36CloseTaskControl()
        }
        control = object : CloseTaskControl by platform {
            override fun removeTask(taskId: Int): Boolean {
                if (!active || SystemClock.elapsedRealtime() >= this@PackageCloseUserService.deadline) throw CloseBackendException("BACKEND_LOST")
                return platform.removeTask(taskId)
            }
        }
        request = incoming; this.deadline = deadline; this.gate = gate
        gate.asBinder().linkToDeath(death, 0); active = true
        if (!allowed()) throw CloseBackendException("BACKEND_LOST")
        JSONObject().put("adapter", profile.name).put("build", Build.FINGERPRINT).put("uid", Process.myUid()).put("operationId", incoming.operationId)
    }
    @Synchronized override fun enumerate(operationId: String): String = envelope {
        session(operationId)
        CloseCodec.snapshot(checkNotNull(control).enumerate())
    }
    @Synchronized override fun removeVerified(operationId: String, expectedJson: String): String = envelope {
        session(operationId)
        CloseCodec.result(VerifiedTaskRemover(checkNotNull(request), checkNotNull(control), ::allowed).remove(CloseCodec.task(JSONObject(expectedJson))))
    }
    private fun session(id: String) {
        if (request?.operationId != id || !allowed()) throw CloseBackendException("BACKEND_LOST")
    }
    private fun allowed(): Boolean {
        if (!active || SystemClock.elapsedRealtime() >= deadline || !gateBusy.compareAndSet(false, true)) return false
        val callback = checkNotNull(gate); val op = checkNotNull(request).operationId
        val call = gateLane.submit<Boolean> {
            try { callback.isAllowed(op, deadline) } finally { gateBusy.set(false) }
        }
        val permitted = try { call.get(250, TimeUnit.MILLISECONDS) } catch (_: Exception) { false }
        if (!permitted || !active || SystemClock.elapsedRealtime() >= deadline) { active = false; return false }
        return true
    }
    override fun destroy() { active = false; gateLane.shutdownNow(); exitProcess(0) }
}
