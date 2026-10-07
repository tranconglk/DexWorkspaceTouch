package com.trancong.dexworkspacetouch.platform.launch.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.*
import com.trancong.dexworkspacetouch.BuildConfig
import rikka.shizuku.Shizuku
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.*

/** The engine sees only WorkspaceCommandShell. All Binder/process details stay behind this adapter. */
class AndroidShizukuCommandTransport(context: Context, serviceTag: String = "dwt-workspace-control-v1") : WorkspaceCommandShell, AutoCloseable {
    // Each owner gets its own service. Async disposal can never remove a newer owner's binding.
    private val transport = ProductionShizukuCommandTransport(AndroidWorkspaceServiceBinding(context.applicationContext,
        "$serviceTag-${UUID.randomUUID()}"))
    internal fun executeCommand(arguments: List<String>, timeoutMs: Long = 3000): ShizukuCommandResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Workspace commands must run off the UI thread" }
        return transport.executeCommand(arguments, timeoutMs)
    }
    override fun execute(arguments: List<String>): WorkspaceCommandResult = executeCommand(arguments).let {
        WorkspaceCommandResult(it.exitCode, it.stdout + "\n" + it.stderr)
    }
    override fun close() = transport.close()
}

internal class AndroidWorkspaceServiceBinding(context: Context, tag: String) : WorkspaceServiceBinding {
    private val args = Shizuku.UserServiceArgs(ComponentName(context, WorkspaceCommandUserService::class.java))
        .daemon(false).processNameSuffix("workspace_control").tag(tag).version(1).debuggable(BuildConfig.DEBUG)
    private val lock = Any()
    private class Slot {
        val connected = CountDownLatch(1)
        val bindFinished = CountDownLatch(1)
        var bindStarted = false
        var service: IWorkspaceCommandService? = null
        var failure: CommandTransportFailure? = null
        lateinit var connection: ServiceConnection
        lateinit var death: IBinder.DeathRecipient
    }
    private var slot: Slot? = null
    private var closed = false
    private val cleanup = Executors.newSingleThreadExecutor { Thread(it, "DWT.WorkspaceUnbind").apply { isDaemon = true } }
    private var pendingCleanup: Future<*>? = null
    private val shizukuDeath = Shizuku.OnBinderDeadListener { markDead(CommandTransportFailure.SHIZUKU_UNAVAILABLE) }
    init { Shizuku.addBinderDeadListener(shizukuDeath) }

    override fun availability(): CommandTransportFailure? = try {
        when {
            closed -> CommandTransportFailure.CLOSED
            !Shizuku.pingBinder() -> CommandTransportFailure.SHIZUKU_UNAVAILABLE
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> CommandTransportFailure.PERMISSION_DENIED
            Shizuku.getUid() != 2000 -> CommandTransportFailure.UID_UNSUPPORTED
            else -> null
        }
    } catch (e: SecurityException) { CommandTransportFailure.PERMISSION_DENIED }
      catch (e: Exception) { CommandTransportFailure.SHIZUKU_UNAVAILABLE }

    override fun connect(timeoutMs: Long): WorkspaceCommandService {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        fun remaining(): Long = (deadline - System.nanoTime()).also {
            if (it <= 0) throw CommandTransportException(CommandTransportFailure.TIMEOUT)
        }
        // A late unbind for an old connection must finish before a new one uses the same service tag.
        val pending = synchronized(lock) { pendingCleanup }
        try { pending?.get(remaining(), TimeUnit.NANOSECONDS) }
        catch (e: TimeoutException) { throw CommandTransportException(CommandTransportFailure.TIMEOUT, e) }
        val current = synchronized(lock) {
            if (closed) throw CommandTransportException(CommandTransportFailure.CLOSED)
            slot ?: Slot().also { fresh ->
                fresh.death = IBinder.DeathRecipient { fail(fresh, CommandTransportFailure.SERVICE_DIED) }
                fresh.connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        synchronized(lock) {
                            if (slot !== fresh || fresh.failure != null || closed) return
                            try {
                                binder.linkToDeath(fresh.death, 0)
                                fresh.service = IWorkspaceCommandService.Stub.asInterface(binder)
                            } catch (e: RemoteException) { fresh.failure = CommandTransportFailure.SERVICE_DIED }
                            fresh.connected.countDown()
                        }
                    }
                    override fun onServiceDisconnected(name: ComponentName) = fail(fresh, CommandTransportFailure.DISCONNECTED)
                    override fun onBindingDied(name: ComponentName) = fail(fresh, CommandTransportFailure.SERVICE_DIED)
                    override fun onNullBinding(name: ComponentName) = fail(fresh, CommandTransportFailure.BIND_FAILED)
                }
                slot = fresh
            }
        }
        val shouldBind = synchronized(lock) {
            if (current.bindStarted) false else { current.bindStarted = true; true }
        }
        if (shouldBind) {
            try {
                synchronized(lock) {
                    if (slot !== current || closed || Thread.currentThread().isInterrupted)
                        throw CommandTransportException(CommandTransportFailure.CANCELLED)
                    current.failure?.let { throw CommandTransportException(it) }
                }
                Shizuku.bindUserService(args, current.connection)
            }
            catch (e: SecurityException) { fail(current, CommandTransportFailure.PERMISSION_DENIED) }
            catch (e: Exception) { fail(current, (e as? CommandTransportException)?.failure ?: CommandTransportFailure.BIND_FAILED) }
            finally { current.bindFinished.countDown() }
        }
        if (!current.connected.await(remaining(), TimeUnit.NANOSECONDS)) throw CommandTransportException(CommandTransportFailure.TIMEOUT)
        val service = synchronized(lock) {
            current.failure?.let { throw CommandTransportException(it) }
            if (slot !== current || closed) throw CommandTransportException(CommandTransportFailure.DISCONNECTED)
            current.service ?: throw CommandTransportException(CommandTransportFailure.BIND_FAILED)
        }
        try {
            if (service.uid != 2000) throw CommandTransportException(CommandTransportFailure.UID_UNSUPPORTED)
        } catch (e: DeadObjectException) { throw CommandTransportException(CommandTransportFailure.SERVICE_DIED, e) }
        return object : WorkspaceCommandService {
            override fun isAlive() = synchronized(lock) {
                slot === current && current.failure == null && service.asBinder().isBinderAlive && !closed
            }
            override fun open(arguments: List<String>, timeoutMs: Long, requestId: String): InputStream {
                if (!isAlive()) throw CommandTransportException(CommandTransportFailure.DISCONNECTED)
                return try {
                    ParcelFileDescriptor.AutoCloseInputStream(service.execute(arguments.toTypedArray(), SystemClock.elapsedRealtime() + timeoutMs, requestId)
                        ?: throw CommandTransportException(CommandTransportFailure.MALFORMED_RESULT))
                } catch (e: DeadObjectException) { throw CommandTransportException(CommandTransportFailure.SERVICE_DIED, e) }
                  catch (e: SecurityException) { throw CommandTransportException(CommandTransportFailure.PERMISSION_DENIED, e) }
                  catch (e: RemoteException) { throw CommandTransportException(CommandTransportFailure.DISCONNECTED, e) }
                  catch (e: IllegalStateException) {
                    throw CommandTransportException(CommandTransportFailure.entries.firstOrNull { it.name == e.message }
                        ?: CommandTransportFailure.EXECUTION_FAILED, e)
                }
            }
        }
    }
    private fun fail(current: Slot, reason: CommandTransportFailure) = synchronized(lock) {
        if (slot === current) { current.failure = reason; current.connected.countDown() }
    }
    private fun markDead(reason: CommandTransportFailure) { synchronized(lock) { slot }?.let { fail(it, reason) } }
    override fun invalidate(requestId: String?) {
        synchronized(lock) {
            if (requestId == null) {
                closed = true
                Shizuku.removeBinderDeadListener(shizukuDeath)
            }
            val old = slot
            slot = null
            if (old != null) {
                old.failure = CommandTransportFailure.DISCONNECTED
                old.connected.countDown()
                pendingCleanup = cleanup.submit {
                    // If the bind RPC is stuck, later calls still have a finite deadline and cannot dispatch.
                    // Never unbind before that RPC finishes and then allow a late bind to resurrect the slot.
                    old.bindFinished.await()
                    val service = old.service
                    if (service != null) {
                        runCatching { service.asBinder().unlinkToDeath(old.death, 0) }
                        if (requestId != null) runCatching { service.cancel(requestId) }
                    }
                    runCatching { Shizuku.unbindUserService(args, old.connection, true) }
                    runCatching { Shizuku.unbindUserService(args, old.connection, false) }
                }
            }
            if (closed) cleanup.shutdown()
        }
    }
}
