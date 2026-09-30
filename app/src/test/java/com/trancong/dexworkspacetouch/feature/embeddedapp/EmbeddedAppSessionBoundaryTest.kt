package com.trancong.dexworkspacetouch.feature.embeddedapp

import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.IEmbeddedAppService
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import sun.misc.Unsafe

class EmbeddedAppSessionBoundaryTest {
    @Test fun readyObserverSeesTheUsableRemoteSynchronously() {
        val session = allocate(EmbeddedAppSession::class.java)
        val lifecycle = EmbeddedAppLifecycleCoordinator().apply { beginConnect() }
        val connection = SessionConnectionCoordinator().apply { beginConnect() }
        val service = Proxy.newProxyInstance(
            IEmbeddedAppService::class.java.classLoader,
            arrayOf(IEmbeddedAppService::class.java),
        ) { _, method, _ -> if (method.name == "getUid") 2000 else null } as IEmbeddedAppService
        val snapshots = mutableListOf<EmbeddedSessionPhase>()
        set(session, "lifecycle", lifecycle)
        set(session, "connectionCoordinator", connection)
        set(session, "state", EmbeddedAppState())
        set(session, "changed", { _: EmbeddedAppState -> })
        set(session, "lifecycleChanged", { snapshot: EmbeddedSessionSnapshot ->
            snapshots += snapshot.phase
            if (snapshot.phase == EmbeddedSessionPhase.READY) {
                assertSame(service, field(session, "remote"))
                session.startSession(allocate(Surface::class.java))
            }
        })
        val listenerClass = Class.forName("${EmbeddedAppSession::class.java.name}\$listener\$1")
        val constructor = listenerClass.declaredConstructors.single().apply { isAccessible = true }
        val listener = constructor.newInstance(session) as EmbeddedAppServiceListener

        listener.onServiceReady(service)

        assertEquals(listOf(EmbeddedSessionPhase.READY, EmbeddedSessionPhase.FAILED), snapshots)
        assertEquals("SURFACE_INVALID", lifecycle.snapshot.failure?.code)
    }

    @Test fun missingRemoteStartPublishesOneStructuredFailure() {
        val session = allocate(EmbeddedAppSession::class.java)
        val lifecycle = EmbeddedAppLifecycleCoordinator().apply { beginConnect(); serviceReady() }
        val snapshots = mutableListOf<EmbeddedSessionSnapshot>()
        set(session, "lifecycle", lifecycle)
        set(session, "state", EmbeddedAppState())
        set(session, "changed", { _: EmbeddedAppState -> })
        set(session, "lifecycleChanged", { snapshot: EmbeddedSessionSnapshot -> snapshots += snapshot })

        session.startSession(allocate(Surface::class.java))
        session.startSession(allocate(Surface::class.java))

        assertEquals(EmbeddedSessionPhase.FAILED, lifecycle.snapshot.phase)
        assertEquals("REMOTE_UNAVAILABLE", lifecycle.snapshot.failure?.code)
        assertEquals(1, snapshots.count { it.phase == EmbeddedSessionPhase.FAILED })
    }

    @Test fun unusableSurfacePublishesStructuredFailure() {
        val session = allocate(EmbeddedAppSession::class.java)
        val lifecycle = EmbeddedAppLifecycleCoordinator().apply { beginConnect(); serviceReady() }
        val service = Proxy.newProxyInstance(
            IEmbeddedAppService::class.java.classLoader,
            arrayOf(IEmbeddedAppService::class.java),
        ) { _, _, _ -> null } as IEmbeddedAppService
        val snapshots = mutableListOf<EmbeddedSessionSnapshot>()
        set(session, "lifecycle", lifecycle)
        set(session, "remote", service)
        set(session, "state", EmbeddedAppState())
        set(session, "changed", { _: EmbeddedAppState -> })
        set(session, "lifecycleChanged", { snapshot: EmbeddedSessionSnapshot -> snapshots += snapshot })

        session.startSession(allocate(Surface::class.java))

        assertEquals("SURFACE_INVALID", lifecycle.snapshot.failure?.code)
        assertEquals(1, snapshots.count { it.phase == EmbeddedSessionPhase.FAILED })
    }

    private fun <T> allocate(type: Class<T>): T {
        val f = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return (f.get(null) as Unsafe).allocateInstance(type) as T
    }

    private fun set(target: Any, name: String, value: Any) {
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
    }

    private fun field(target: Any, name: String): Any? =
        target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
}
