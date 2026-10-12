package com.trancong.dexworkspacetouch.workspace.close

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.workspace.close.remote.ICloseSessionGate
import com.trancong.dexworkspacetouch.workspace.close.remote.IPackageCloseService
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import rikka.shizuku.Shizuku

/** No permission prompt, silent rebind or fallback inside an accepted operation. */
class ShizukuCloseTaskBackend(private val context: Context) : CloseTaskBackend {
    private val lane = CloseRpcLane()
    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName,
        "com.trancong.dexworkspacetouch.workspace.close.remote.PackageCloseUserService"))
        .daemon(false).processNameSuffix("workspace_close").tag("dwt-wsc-004a").version(4001).debuggable(BuildConfig.DEBUG)
    @Volatile private var active = false
    private val stateLock = Any()
    @Volatile private var generation = 0L
    @Volatile private var remote: IPackageCloseService? = null
    private var connection: ServiceConnection? = null
    private var request: PackageCloseRequest? = null
    private var deadline = 0L
    private var gate: ICloseSessionGate? = null
    private var serviceDeath: IBinder.DeathRecipient? = null
    private val serverDeath = Shizuku.OnBinderDeadListener { active = false }
    override val quiescent: Boolean get() = lane.quiescent

    override suspend fun checkAvailable() = lane.call { probeAvailable() }
    private fun probeAvailable() {
        try {
            if (!Shizuku.pingBinder()) throw CloseBackendException("BACKEND_LOST")
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) throw CloseBackendException("PERMISSION_REVOKED")
            if (Shizuku.getUid() != 2000) throw CloseBackendException("SHELL_UID_REQUIRED")
        } catch (e: CloseBackendException) { throw e }
        catch (_: Exception) { throw CloseBackendException("BACKEND_LOST") }
    }
    override suspend fun open(request: PackageCloseRequest, deadline: Long) {
        checkAvailable()
        CloseAdapterProfile.select(Build.VERSION.SDK_INT, Build.FINGERPRINT)
        val currentGeneration = synchronized(stateLock) {
            this.request = request; this.deadline = deadline; active = true; ++generation
        }
        Shizuku.addBinderDeadListener(serverDeath)
        val connected = CompletableDeferred<IPackageCloseService>()
        gate = object : ICloseSessionGate.Stub() {
            override fun isAllowed(operationId: String, deadline: Long): Boolean {
                fun valid() = active && generation == currentGeneration && operationId == request.operationId &&
                    deadline == this@ShizukuCloseTaskBackend.deadline && SystemClock.elapsedRealtime() < deadline
                if (Binder.getCallingUid() != 2000 || !valid()) return false
                val identity = Binder.clearCallingIdentity()
                return try { probeAvailable(); valid() } catch (_: Exception) { false }
                finally { Binder.restoreCallingIdentity(identity) }
            }
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                synchronized(stateLock) {
                    if (!active || generation != currentGeneration) return
                    val death = IBinder.DeathRecipient {
                        synchronized(stateLock) { if (generation == currentGeneration) active = false }
                    }
                    try {
                        binder.linkToDeath(death, 0)
                        serviceDeath = death
                        val service = IPackageCloseService.Stub.asInterface(binder)
                        remote = service; connected.complete(service)
                    } catch (_: Exception) { active = false; connected.completeExceptionally(CloseBackendException("BACKEND_LOST")) }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                synchronized(stateLock) { if (generation == currentGeneration) active = false }
                connected.completeExceptionally(CloseBackendException("BACKEND_LOST"))
            }
        }
        connection = conn
        lane.call { Shizuku.bindUserService(args, conn) }
        val service = connected.await()
        lane.call {
            checkSession()
            if (service.uid != 2000) throw CloseBackendException("SHELL_UID_REQUIRED")
            val identity = evidence(service.openSession(CloseCodec.request(request).toString(), deadline, gate))
            check(identity.getString("operationId") == request.operationId && identity.getString("build") == Build.FINGERPRINT && identity.getInt("uid") == 2000)
        }
    }
    private fun checkSession() {
        probeAvailable()
        if (!active || SystemClock.elapsedRealtime() >= deadline) throw CloseBackendException("BACKEND_LOST")
    }
    private fun evidence(json: String): JSONObject {
        val envelope = JSONObject(json)
        if (!envelope.getBoolean("success")) throw CloseBackendException(envelope.getString("reason"))
        return envelope.getJSONObject("evidence")
    }
    override suspend fun enumerate(): CloseSnapshot = lane.call {
        checkSession()
        CloseCodec.snapshot(evidence(checkNotNull(remote).enumerate(checkNotNull(request).operationId)))
    }
    override suspend fun removeVerified(expected: CloseTask): CloseTaskResult = lane.call {
        checkSession()
        CloseCodec.result(evidence(checkNotNull(remote).removeVerified(checkNotNull(request).operationId, CloseCodec.task(expected).toString())))
    }
    override fun invalidate() {
        val old = synchronized(stateLock) {
            active = false; generation++
            val old = Teardown(connection, remote, gate, serviceDeath)
            connection = null; remote = null; gate = null; serviceDeath = null
            old
        }
        val oldConnection = old.connection
        if (oldConnection != null) lane.afterQuiescent {
            runCatching { old.death?.let { old.remote?.asBinder()?.unlinkToDeath(it, 0) } }
            runCatching { Shizuku.unbindUserService(args, oldConnection, true) }
            old.gate?.asBinder()
        }
        runCatching { Shizuku.removeBinderDeadListener(serverDeath) }
    }
    private data class Teardown(val connection: ServiceConnection?, val remote: IPackageCloseService?, val gate: ICloseSessionGate?, val death: IBinder.DeathRecipient?)
}
