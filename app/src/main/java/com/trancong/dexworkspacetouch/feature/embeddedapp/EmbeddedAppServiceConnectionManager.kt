package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.trancong.dexworkspacetouch.BuildConfig
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
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
    private val enqueue: (Runnable) -> Boolean = { it.run(); true },
) {
    private val leases = linkedSetOf<String>()
    private val inFlight = linkedMapOf<Long, String>()
    private val nonTerminal = linkedSetOf<String>()
    private var removed = false
    private var querying = false
    private var queryPending = false
    private var removing = false
    private var revision = 0L
    private var operationSequence = 0L
    val leaseCount: Int get() = synchronized(this) { leases.size }
    internal val inventoryRevision: Long get() = synchronized(this) { revision }

    @Synchronized fun acquire(id: String): Boolean {
        if (id.isBlank() || removing || id in leases) return false
        removed = false
        leases.add(id); revision++
        return true
    }
    @Synchronized fun owns(id: String): Boolean = id in leases
    @Synchronized internal fun invalidate() { revision++ }
    @Synchronized fun operationStarted(id: String): Long {
        check(id in leases)
        val operation = ++operationSequence
        inFlight[operation] = id
        nonTerminal.add(id); revision++
        return operation
    }
    fun operationFinished(id: String, terminal: Boolean, operation: Long? = null): Boolean {
        val clean = synchronized(this) {
            // Legacy single-operation callers cannot choose an operation during overlap.
            val exact = operation ?: inFlight.filterValues { it == id }.keys.singleOrNull() ?: return false
            if (inFlight[exact] != id) return false
            inFlight.remove(exact)
            val authoritative = terminal && !inFlight.containsValue(id)
            if (authoritative) nonTerminal.remove(id)
            revision++
            authoritative
        }
        maybeFinalizeServiceRemoval()
        return clean
    }
    @Synchronized internal fun mayReconcile(id: String): Boolean =
        id in nonTerminal && inFlight.isEmpty()
    fun reconcileAbsent(
        id: String,
        sameService: Boolean,
        sessionAbsent: Boolean,
        remoteState: EmbeddedAppServiceState?,
        expectedRevision: Long? = null,
    ): Boolean {
        synchronized(this) {
            if (!sameService || !sessionAbsent || remoteState?.provesEmpty != true ||
                inFlight.isNotEmpty() || id !in nonTerminal ||
                (expectedRevision != null && revision != expectedRevision)) return false
            nonTerminal.remove(id); revision++
        }
        maybeFinalizeServiceRemoval()
        return true
    }
    fun release(id: String) {
        synchronized(this) { if (leases.remove(id)) revision++ }
        maybeFinalizeServiceRemoval()
    }
    fun maybeFinalizeServiceRemoval() {
        val expected = synchronized(this) {
            if (removed || removing || !emptyInventory()) return
            if (querying) { queryPending = true; return }
            querying = true
            revision
        }
        if (!enqueue(Runnable { finalize(expected) })) synchronized(this) { querying = false }
    }
    private fun finalize(expected: Long) {
        val state = runCatching(queryRemote).getOrNull()
        var freshEvidenceRequested = false
        val reserved = synchronized(this) {
            querying = false
            freshEvidenceRequested = queryPending && revision != expected && emptyInventory()
            queryPending = false
            if (revision != expected || !emptyInventory() || state?.provesEmpty != true) false
            else { removing = true; true }
        }
        if (!reserved) {
            if (freshEvidenceRequested) maybeFinalizeServiceRemoval()
            return
        }
        // Failed/hung removal keeps admission closed; cancellation is not removal evidence.
        if (runCatching(removeService).isSuccess) synchronized(this) {
            removed = true; removing = false; revision++
        }
    }
    private fun emptyInventory() = leases.isEmpty() && inFlight.isEmpty() && nonTerminal.isEmpty()
}

internal data class ConnectionIdentity(
    val sessionId: String,
    val registration: Long,
    val generation: Long,
    val operation: Long,
    val binderIdentity: Long,
)

/** Linearizes verification against generation invalidation without callbacks under either lock. */
internal class ConnectionReadinessFence {
    private val pending = AtomicBoolean(true)
    fun claim(): Boolean = pending.compareAndSet(true, false)
    fun invalidate() { pending.set(false) }
}

internal sealed interface EmbeddedConnectionEvent {
    val identity: ConnectionIdentity
    data class Ready(
        override val identity: ConnectionIdentity,
        val service: IEmbeddedAppService,
        val readiness: ConnectionReadinessFence,
    ) : EmbeddedConnectionEvent
    data class Disconnected(override val identity: ConnectionIdentity) : EmbeddedConnectionEvent
    data class Failed(override val identity: ConnectionIdentity, val code: String) : EmbeddedConnectionEvent
}

internal interface EmbeddedAppServiceListener {
    fun onConnectionEvent(event: EmbeddedConnectionEvent) {
        when (event) {
            is EmbeddedConnectionEvent.Ready -> onServiceReady(event.service)
            is EmbeddedConnectionEvent.Disconnected -> onServiceDisconnected()
            is EmbeddedConnectionEvent.Failed -> Unit
        }
    }
    fun onServiceReady(service: IEmbeddedAppService) = Unit
    fun onServiceDisconnected() = Unit
}

internal interface EmbeddedConnectionTransport {
    fun install(received: () -> Unit)
    fun requestConnect(connection: ServiceConnection)
    fun unbind(connection: ServiceConnection)
    fun uninstall()
    fun serviceState(service: IEmbeddedAppService): EmbeddedAppServiceState? {
        val bundle = service.serviceState?.takeIf { it.getBoolean("success") } ?: return null
        val keys = listOf("activeSessionCount", "startingSessionCount", "stoppingSessionCount", "liveResourceSessionCount")
        if (keys.any { !bundle.containsKey(it) }) return null
        return runCatching {
            EmbeddedAppServiceState(bundle.getInt(keys[0]), bundle.getInt(keys[1]),
                bundle.getInt(keys[2]), bundle.getInt(keys[3]))
        }.getOrNull()
    }
    fun sessionAbsent(service: IEmbeddedAppService, id: String): Boolean {
        val state = service.getSessionState(id) ?: return false
        return !state.getBoolean("success") && state.getString("failureCode") == "UNKNOWN_SESSION" &&
            state.getString("sessionId") == id
    }
}

class EmbeddedAppServiceLease internal constructor(
    private val owner: EmbeddedAppServiceConnectionManager,
    internal val sessionId: String,
    internal val registration: Long,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    fun connect() { check(!closed.get()); owner.connect(this) }
    fun operationStarted(): Long { check(!closed.get()); return owner.operationStarted(this) }
    fun operationFinished(terminal: Boolean, operation: Long? = null): Boolean =
        owner.operationFinished(this, terminal, operation)
    internal fun accepts(event: EmbeddedConnectionEvent): Boolean = !closed.get() && owner.isCurrent(event)
    fun detachNotifications() = owner.detach(this)
    override fun close() { if (closed.compareAndSet(false, true)) owner.release(this) }
}

class EmbeddedAppServiceConnectionManager internal constructor(
    private val transport: EmbeddedConnectionTransport,
    private val transportLane: WorkAdmission = EmbeddedConnectionLanes.transport,
    private val notifications: Executor = EmbeddedConnectionLanes.notificationExecutor,
    finalizationLane: WorkAdmission = EmbeddedConnectionLanes.finalization,
) {
    private val bookkeeper = ConnectionLeaseBookkeeper()
    private val finalization = FinalRemovalCoordinator(::queryRemoteState, ::removeService, finalizationLane::tryExecute)
    private val listeners = linkedMapOf<String, Registration>()
    private var remote: IEmbeddedAppService? = null
    private var serviceGeneration = 0L
    private var sequence = 0L
    private var binderIdentity = 0L
    private var connection: ServiceConnection? = null
    private var listenersInstalled = false
    private var removalCandidate: ServiceSnapshot? = null
    private var removingConnection: ServiceConnection? = null

    private class Registration(
        val id: Long,
        val mailbox: DetachableMailbox<EmbeddedConnectionEvent>,
        var operation: Long = 0,
        var readiness: ConnectionReadinessFence? = null,
    )
    private data class ServiceSnapshot(val service: IEmbeddedAppService, val generation: Long, val connection: ServiceConnection?)

    internal fun acquire(sessionId: EmbeddedAppSessionId, listener: EmbeddedAppServiceListener): EmbeddedAppServiceLease =
        synchronized(this) {
            check(!bookkeeper.mayBind(sessionId.value)) { "Duplicate connection lease" }
            check(finalization.acquire(sessionId.value)) { "Connection admission busy" }
            check(bookkeeper.acquire(sessionId.value))
            val id = ++sequence
            val mailbox = DetachableMailbox<EmbeddedConnectionEvent>(notifications) { event ->
                if (isCurrent(event)) listener.onConnectionEvent(event)
            }
            listeners[sessionId.value] = Registration(id, mailbox)
            EmbeddedAppServiceLease(this, sessionId.value, id)
        }

    internal fun connect(lease: EmbeddedAppServiceLease) {
        var delivery: Pair<Registration, EmbeddedConnectionEvent>? = null
        val bound = synchronized(this) {
            val registration = registrationFor(lease)
            registration.operation = ++sequence
            val service = remote
            if (service != null) {
                delivery = registration to readyEvent(lease.sessionId, registration, service)
                null
            } else if (connection != null) null
            else newConnection().also { connection = it }
        }
        delivery?.let { (registration, event) ->
            check(registration.mailbox.offer(event)) { "Notification admission exhausted" }
        }
        if (bound != null) enqueueConnect(bound)
    }

    private fun newConnection(): ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = IEmbeddedAppService.Stub.asInterface(binder)
            val deliveries = synchronized(this@EmbeddedAppServiceConnectionManager) {
                if (connection !== this || removingConnection != null) return
                serviceGeneration++; binderIdentity = ++sequence; remote = service
                finalization.invalidate()
                listeners.map { (id, registration) ->
                    registration to readyEvent(id, registration, service)
                }
            }
            deliver(deliveries)
            finalization.maybeFinalizeServiceRemoval()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            val deliveries = synchronized(this@EmbeddedAppServiceConnectionManager) {
                if (connection !== this || removingConnection != null) return
                serviceGeneration++; remote = null; connection = null
                finalization.invalidate()
                listeners.map { (id, registration) ->
                    registration.readiness?.invalidate()
                    registration to EmbeddedConnectionEvent.Disconnected(identity(id, registration))
                }
            }
            deliver(deliveries)
        }
    }

    private fun enqueueConnect(bound: ServiceConnection) {
        if (!transportLane.tryExecute(Runnable {
                if (synchronized(this) { connection !== bound || bookkeeper.leaseCount == 0 }) return@Runnable
                try {
                    val install = synchronized(this) {
                        if (listenersInstalled) false else { listenersInstalled = true; true }
                    }
                    if (install) transport.install(::requestBind)
                    if (synchronized(this) { connection === bound && bookkeeper.leaseCount > 0 }) {
                        transport.requestConnect(bound)
                    }
                } catch (_: Exception) { connectFailed(bound, "CONNECTION_TRANSPORT_FAILED") }
            })) connectFailed(bound, "CONNECTION_CAPACITY_EXHAUSTED")
    }
    private fun requestBind() {
        val bound = synchronized(this) { connection.takeIf { bookkeeper.leaseCount > 0 && removingConnection == null } }
        if (bound != null) enqueueConnect(bound)
    }
    private fun connectFailed(bound: ServiceConnection, code: String) {
        val deliveries = synchronized(this) {
            if (connection !== bound || remote != null) return
            connection = null; serviceGeneration++; finalization.invalidate()
            listeners.map { (id, registration) ->
                registration.readiness?.invalidate()
                registration to EmbeddedConnectionEvent.Failed(identity(id, registration), code)
            }
        }
        deliver(deliveries)
    }
    private fun deliver(deliveries: List<Pair<Registration, EmbeddedConnectionEvent>>) {
        deliveries.forEach { (registration, event) -> registration.mailbox.offer(event) }
        // Rejected delivery cannot authorize READY; the runner's deadline remains authoritative.
    }
    private fun identity(id: String, registration: Registration) =
        ConnectionIdentity(id, registration.id, serviceGeneration, registration.operation, binderIdentity)
    private fun readyEvent(id: String, registration: Registration, service: IEmbeddedAppService): EmbeddedConnectionEvent.Ready {
        registration.readiness?.invalidate()
        val fence = ConnectionReadinessFence()
        registration.readiness = fence
        return EmbeddedConnectionEvent.Ready(identity(id, registration), service, fence)
    }
    @Synchronized internal fun isCurrent(event: EmbeddedConnectionEvent): Boolean {
        val value = event.identity
        val registration = listeners[value.sessionId] ?: return false
        return registration.id == value.registration && registration.operation == value.operation &&
            serviceGeneration == value.generation && removingConnection == null &&
            (event !is EmbeddedConnectionEvent.Ready || (remote === event.service && binderIdentity == value.binderIdentity))
    }
    private fun registrationFor(lease: EmbeddedAppServiceLease): Registration =
        listeners[lease.sessionId]?.takeIf { it.id == lease.registration }
            ?: error("Binding requires an owning registration")

    @Synchronized internal fun operationStarted(lease: EmbeddedAppServiceLease): Long {
        registrationFor(lease)
        return finalization.operationStarted(lease.sessionId)
    }
    internal fun operationFinished(lease: EmbeddedAppServiceLease, terminal: Boolean, operation: Long?): Boolean =
        finalization.operationFinished(lease.sessionId, terminal, operation)
    @Synchronized internal fun generationFor(service: IEmbeddedAppService): Long? =
        serviceGeneration.takeIf { remote === service && removingConnection == null }

    internal fun reconcileAbsentSession(lease: EmbeddedAppServiceLease, service: IEmbeddedAppService, generation: Long?): Boolean {
        val revision = synchronized(this) {
            if (generation == null || serviceGeneration != generation || remote !== service ||
                !finalization.owns(lease.sessionId) || listeners[lease.sessionId]?.id != lease.registration ||
                !finalization.mayReconcile(lease.sessionId)) return false
            finalization.inventoryRevision
        }
        if (runCatching { service.asBinder()?.isBinderAlive }.getOrNull() != true) return false
        val absent = runCatching { transport.sessionAbsent(service, lease.sessionId) }.getOrDefault(false)
        val state = if (absent) runCatching { transport.serviceState(service) }.getOrNull() else null
        if (runCatching { service.asBinder()?.isBinderAlive }.getOrNull() != true) return false
        val current = synchronized(this) {
            serviceGeneration == generation && remote === service && listeners[lease.sessionId]?.id == lease.registration
        }
        return finalization.reconcileAbsent(lease.sessionId, current, absent, state, revision)
    }

    internal fun detach(lease: EmbeddedAppServiceLease) {
        synchronized(this) {
            listeners[lease.sessionId]?.takeIf { it.id == lease.registration }?.let {
                it.readiness?.invalidate(); it.mailbox.detach()
            }
        }
    }
    internal fun release(lease: EmbeddedAppServiceLease) {
        val uninstall = synchronized(this) {
            val registration = listeners[lease.sessionId] ?: return
            if (registration.id != lease.registration) return
            registration.readiness?.invalidate(); registration.mailbox.detach(); listeners.remove(lease.sessionId)
            bookkeeper.release(lease.sessionId) && connection == null
        }
        finalization.release(lease.sessionId)
        if (uninstall) enqueueUninstall()
    }
    private fun queryRemoteState(): EmbeddedAppServiceState? {
        val candidate = synchronized(this) {
            remote?.let { ServiceSnapshot(it, serviceGeneration, connection) }
        } ?: return null
        val state = runCatching { transport.serviceState(candidate.service) }.getOrNull()
        return synchronized(this) {
            if (remote !== candidate.service || serviceGeneration != candidate.generation) null
            else { removalCandidate = candidate; state }
        }
    }
    private fun removeService() {
        val candidate = synchronized(this) {
            val value = checkNotNull(removalCandidate)
            check(remote === value.service && serviceGeneration == value.generation && connection === value.connection)
            check(bookkeeper.leaseCount == 0)
            removingConnection = checkNotNull(value.connection)
            value
        }
        transport.unbind(checkNotNull(candidate.connection))
        synchronized(this) {
            check(removingConnection === candidate.connection && serviceGeneration == candidate.generation)
            serviceGeneration++; remote = null; connection = null; removingConnection = null; removalCandidate = null
        }
        enqueueUninstall()
    }
    private fun enqueueUninstall() {
        transportLane.tryExecute(Runnable {
            val uninstall = synchronized(this) {
                if (!listenersInstalled || bookkeeper.leaseCount != 0 || connection != null) false
                else { listenersInstalled = false; true }
            }
            if (uninstall) transport.uninstall()
        })
    }

    companion object {
        @Volatile private var instance: EmbeddedAppServiceConnectionManager? = null
        fun get(context: Context): EmbeddedAppServiceConnectionManager = instance ?: synchronized(this) {
            instance ?: EmbeddedAppServiceConnectionManager(AndroidConnectionTransport(context.applicationContext))
                .also { instance = it }
        }
    }
}

private class AndroidConnectionTransport(app: Context) : EmbeddedConnectionTransport {
    private val args = Shizuku.UserServiceArgs(ComponentName(app.packageName,
        "com.trancong.dexworkspacetouch.feature.embeddedapp.remote.EmbeddedAppUserService"))
        .daemon(false).processNameSuffix("embedded_app").tag("dwt-vdm-002")
        .version(BuildConfig.VERSION_CODE + 1001).debuggable(BuildConfig.DEBUG)
    private var received: Shizuku.OnBinderReceivedListener? = null
    private var permission: Shizuku.OnRequestPermissionResultListener? = null
    private var bound: ServiceConnection? = null
    override fun install(received: () -> Unit) {
        this.received = Shizuku.OnBinderReceivedListener(received).also(Shizuku::addBinderReceivedListenerSticky)
        permission = Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == REQUEST && result == PackageManager.PERMISSION_GRANTED) received()
        }.also(Shizuku::addRequestPermissionResultListener)
    }
    override fun requestConnect(connection: ServiceConnection) {
        if (!Shizuku.pingBinder()) return
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(REQUEST)
            return
        }
        if (bound === connection) return
        bound = connection
        Shizuku.bindUserService(args, connection)
    }
    override fun unbind(connection: ServiceConnection) {
        Shizuku.unbindUserService(args, connection, true)
    }
    override fun uninstall() {
        received?.let(Shizuku::removeBinderReceivedListener)
        permission?.let(Shizuku::removeRequestPermissionResultListener)
        received = null; permission = null; bound = null
    }
    companion object { private const val REQUEST = 7101 }
}
