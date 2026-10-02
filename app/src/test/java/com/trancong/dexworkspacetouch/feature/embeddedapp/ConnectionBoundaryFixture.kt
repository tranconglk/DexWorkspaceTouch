package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.lang.reflect.Proxy
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import sun.misc.Unsafe

internal class ManualLane : AbstractExecutorService(), WorkAdmission {
    val tasks = ConcurrentLinkedQueue<Runnable>()
    private var stopped = false
    override fun execute(command: Runnable) { tasks.add(command) }
    override fun tryExecute(task: Runnable): Boolean { execute(task); return true }
    fun drain() { while (true) (tasks.poll() ?: return).run() }
    override fun shutdown() { stopped = true }
    override fun shutdownNow(): MutableList<Runnable> { stopped = true; return mutableListOf() }
    override fun isShutdown() = stopped
    override fun isTerminated() = stopped && tasks.isEmpty()
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = isTerminated
}

internal class FakeConnectionTransport : EmbeddedConnectionTransport {
    val bindings = mutableListOf<ServiceConnection>()
    var queries = 0
    var removals = 0
    var onQuery: () -> EmbeddedAppServiceState? = { EmbeddedAppServiceState(0, 0, 0, 0) }
    var onUnbind: () -> Unit = {}
    var onConnect: () -> Unit = {}
    var onAbsent: () -> Boolean = { true }
    override fun install(received: () -> Unit) = Unit
    override fun requestConnect(connection: ServiceConnection) { bindings += connection; onConnect() }
    override fun uninstall() = Unit
    override fun unbind(connection: ServiceConnection) { onUnbind(); removals++ }
    override fun serviceState(service: IEmbeddedAppService): EmbeddedAppServiceState? { queries++; return onQuery() }
    override fun sessionAbsent(service: IEmbeddedAppService, id: String) = onAbsent()
    fun connected(service: IEmbeddedAppService, index: Int = bindings.lastIndex) {
        bindings[index].onServiceConnected(allocateAndroid(ComponentName::class.java), service.asBinder())
    }
    fun disconnected(index: Int = bindings.lastIndex) {
        bindings[index].onServiceDisconnected(allocateAndroid(ComponentName::class.java))
    }
}

internal fun fakeUidService(uid: () -> Int = { 2000 }): IEmbeddedAppService {
    lateinit var service: IEmbeddedAppService
    val binder = Proxy.newProxyInstance(IBinder::class.java.classLoader, arrayOf(IBinder::class.java)) { _, method, _ ->
        when (method.name) {
            "queryLocalInterface" -> service
            "isBinderAlive" -> true
            else -> null
        }
    } as IBinder
    service = Proxy.newProxyInstance(IEmbeddedAppService::class.java.classLoader,
        arrayOf(IEmbeddedAppService::class.java)) { _, method, _ ->
        when (method.name) {
            "getUid" -> uid()
            "asBinder" -> binder
            else -> null
        }
    } as IEmbeddedAppService
    return service
}

internal fun <T> allocateAndroid(type: Class<T>): T {
    val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    return (field.get(null) as Unsafe).allocateInstance(type) as T
}
