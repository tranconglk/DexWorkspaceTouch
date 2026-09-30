package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import rikka.shizuku.Shizuku

class ConnectionLeaseBookkeeper {
    private val ids = linkedSetOf<String>()
    val leaseCount: Int get() = ids.size
    @Synchronized fun acquire(id: String): Boolean = id.isNotBlank() && ids.add(id)
    @Synchronized fun mayBind(id: String): Boolean = id in ids
    @Synchronized fun release(id: String): Boolean { ids.remove(id); return ids.isEmpty() }
}

class FinalRemovalCoordinator(
    private val queryRemote: () -> EmbeddedAppServiceState?,
    private val removeService: () -> Unit,
) {
    private val leases = linkedSetOf<String>()
    private val inFlight = linkedSetOf<String>()
    private val nonTerminal = linkedSetOf<String>()
    private var removed = false
    val leaseCount: Int get() = leases.size

    @Synchronized fun acquire(id: String): Boolean {
        if (id.isBlank()) return false
        if (removed) {
            check(leases.isEmpty() && inFlight.isEmpty() && nonTerminal.isEmpty())
            removed = false
        }
        return leases.add(id)
    }
    @Synchronized fun owns(id: String): Boolean = id in leases
    @Synchronized fun operationStarted(id: String) {
        check(id in leases); if (inFlight.add(id)) nonTerminal.add(id)
    }
    @Synchronized fun operationFinished(id: String, terminal: Boolean) {
        if (!inFlight.remove(id)) return
        if (terminal) nonTerminal.remove(id)
        maybeFinalizeServiceRemoval()
    }
    @Synchronized fun reconcileAbsent(
        id: String,
        sameService: Boolean,
        sessionAbsent: Boolean,
        remoteState: EmbeddedAppServiceState?,
    ): Boolean {
        if (!sameService || !sessionAbsent || remoteState?.provesEmpty != true ||
            inFlight.isNotEmpty() || id !in nonTerminal) return false
        nonTerminal.remove(id)
        maybeFinalizeServiceRemoval()
        return true
    }
    @Synchronized fun release(id: String) {
        leases.remove(id)
        maybeFinalizeServiceRemoval()
    }
    @Synchronized fun maybeFinalizeServiceRemoval() {
        if (removed || leases.isNotEmpty() || inFlight.isNotEmpty() || nonTerminal.isNotEmpty()) return
        val state = runCatching(queryRemote).getOrNull() ?: return
        if (!state.provesEmpty) return
        removeService(); removed = true
    }
}

interface EmbeddedAppServiceListener {
    fun onServiceReady(service: IEmbeddedAppService)
    fun onServiceDisconnected()
}

class EmbeddedAppServiceLease internal constructor(
    private val owner: EmbeddedAppServiceConnectionManager,
    internal val sessionId: String,
) : AutoCloseable {
    private var closed = false
    fun connect() { check(!closed); owner.connect(this) }
    fun operationStarted() { check(!closed); owner.operationStarted(this) }
    fun operationFinished(terminal: Boolean) { owner.operationFinished(this, terminal) }
    override fun close() { if (!closed) { closed = true; owner.release(this) } }
}

class EmbeddedAppServiceConnectionManager private constructor(context: Context) {
    private val app = context.applicationContext
    private val bookkeeper = ConnectionLeaseBookkeeper()
    private val finalization = FinalRemovalCoordinator(::queryRemoteState, ::removeService)
    private val listeners = linkedMapOf<String, EmbeddedAppServiceListener>()
    private val args = Shizuku.UserServiceArgs(ComponentName(app.packageName,
        "com.trancong.dexworkspacetouch.feature.embeddedapp.remote.EmbeddedAppUserService"))
        .daemon(false).processNameSuffix("embedded_app").tag("dwt-vdm-002")
        .version(BuildConfig.VERSION_CODE + 1001).debuggable(BuildConfig.DEBUG)
    private var remote: IEmbeddedAppService? = null
    private var serviceGeneration = 0L
    private var connection: ServiceConnection? = null
    private var listenersInstalled = false
    private val received = Shizuku.OnBinderReceivedListener { requestBindIfPermitted() }
    private val permission = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code == REQUEST && result == PackageManager.PERMISSION_GRANTED) requestBindIfPermitted()
    }

    @Synchronized fun acquire(sessionId: EmbeddedAppSessionId,
        listener: EmbeddedAppServiceListener): EmbeddedAppServiceLease {
        check(bookkeeper.acquire(sessionId.value)) { "Duplicate connection lease" }
        check(finalization.acquire(sessionId.value)) { "Duplicate finalization lease" }
        listeners[sessionId.value] = listener
        if (!listenersInstalled) {
            Shizuku.addBinderReceivedListenerSticky(received)
            Shizuku.addRequestPermissionResultListener(permission)
            listenersInstalled = true
        }
        return EmbeddedAppServiceLease(this, sessionId.value)
    }

    @Synchronized internal fun connect(lease: EmbeddedAppServiceLease) {
        check(bookkeeper.mayBind(lease.sessionId)) { "Binding requires an owning lease" }
        remote?.let {
            listeners[lease.sessionId]?.onServiceReady(it)
            return
        }
        if (connection != null) return
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(REQUEST)
        else bind()
    }

    @Synchronized internal fun operationStarted(lease: EmbeddedAppServiceLease) {
        finalization.operationStarted(lease.sessionId)
    }

    @Synchronized internal fun operationFinished(lease: EmbeddedAppServiceLease, terminal: Boolean) {
        finalization.operationFinished(lease.sessionId, terminal)
    }

    @Synchronized internal fun generationFor(service: IEmbeddedAppService): Long? =
        serviceGeneration.takeIf { remote === service }

    @Synchronized internal fun reconcileAbsentSession(
        lease: EmbeddedAppServiceLease,
        service: IEmbeddedAppService,
        generation: Long?,
    ): Boolean {
        if (generation == null || serviceGeneration != generation || remote !== service ||
            !finalization.owns(lease.sessionId) || service.asBinder()?.isBinderAlive != true) return false
        val sessionState = runCatching { service.getSessionState(lease.sessionId) }.getOrNull()
        val absent = sessionState?.getBoolean("success") == false &&
            sessionState.getString("failureCode") == "UNKNOWN_SESSION" &&
            sessionState.getString("sessionId") == lease.sessionId
        if (!absent) return false
        val state = queryRemoteState()
        if (serviceGeneration != generation || remote !== service ||
            service.asBinder()?.isBinderAlive != true) return false
        return finalization.reconcileAbsent(lease.sessionId, true, true, state)
    }

    @Synchronized private fun requestBindIfPermitted() {
        if (bookkeeper.leaseCount > 0 && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind()
    }

    @Synchronized private fun bind() {
        if (connection != null || bookkeeper.leaseCount == 0) return
        val created = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val service = IEmbeddedAppService.Stub.asInterface(binder)
                synchronized(this@EmbeddedAppServiceConnectionManager) {
                    if (connection !== this) return
                    serviceGeneration++
                    remote = service
                    listeners.values.toList().forEach { it.onServiceReady(service) }
                }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                synchronized(this@EmbeddedAppServiceConnectionManager) {
                    if (connection !== this) return
                    serviceGeneration++
                    remote = null; connection = null
                    listeners.values.toList().forEach { it.onServiceDisconnected() }
                }
            }
        }
        connection = created
        runCatching { Shizuku.bindUserService(args, created) }.onFailure { connection = null }
    }

    @Synchronized internal fun release(lease: EmbeddedAppServiceLease) {
        listeners.remove(lease.sessionId)
        val last = bookkeeper.release(lease.sessionId)
        finalization.release(lease.sessionId)
        if (last && connection == null) uninstallListeners()
    }

    @Synchronized private fun queryRemoteState(): EmbeddedAppServiceState? {
        val service = remote ?: return null
        val bundle = runCatching { service.serviceState }.getOrNull()
            ?.takeIf { it.getBoolean("success") } ?: return null
        val keys = listOf("activeSessionCount", "startingSessionCount",
            "stoppingSessionCount", "liveResourceSessionCount")
        if (keys.any { !bundle.containsKey(it) }) return null
        return runCatching {
            EmbeddedAppServiceState(bundle.getInt("activeSessionCount"),
                bundle.getInt("startingSessionCount"), bundle.getInt("stoppingSessionCount"),
                bundle.getInt("liveResourceSessionCount"))
        }.getOrNull()
    }

    @Synchronized private fun removeService() {
        val bound = connection ?: return
        Shizuku.unbindUserService(args, bound, true)
        serviceGeneration++
        remote = null; connection = null; uninstallListeners()
    }

    private fun uninstallListeners() {
        if (!listenersInstalled) return
        Shizuku.removeBinderReceivedListener(received)
        Shizuku.removeRequestPermissionResultListener(permission)
        listenersInstalled = false
    }

    companion object {
        private const val REQUEST = 7101
        @Volatile private var instance: EmbeddedAppServiceConnectionManager? = null
        fun get(context: Context): EmbeddedAppServiceConnectionManager = instance ?: synchronized(this) {
            instance ?: EmbeddedAppServiceConnectionManager(context).also { instance = it }
        }
    }
}
