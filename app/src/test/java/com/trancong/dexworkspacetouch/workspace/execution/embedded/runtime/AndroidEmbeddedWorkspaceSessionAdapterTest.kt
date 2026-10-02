package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import android.content.Context
import android.content.ContextWrapper
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppSessionId
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionPhase
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedSessionSnapshot
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedTouchEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import sun.misc.Unsafe

class AndroidEmbeddedWorkspaceSessionAdapterTest {
    @Test
    fun `factory passes target unchanged into exactly one generic session`() {
        val provider = FakeSessionProvider()
        val factory = factory(provider)
        val target = target()

        factory.create(target) {}

        assertEquals(1, provider.created.size)
        assertSame(target, provider.created.single().target)
    }

    @Test
    fun `wrapper validity reflects current underlying surface validity`() {
        val surface = surface()
        var valid = true
        val wrapper = AndroidEmbeddedWorkspaceExecutionSurface(surface) { valid }

        assertTrue(wrapper.isValid)
        valid = false
        assertFalse(wrapper.isValid)
    }

    @Test
    fun `start unwraps exact host surface`() {
        val provider = FakeSessionProvider()
        val handle = factory(provider).create(target()) {}
        val surface = surface()

        handle.start(AndroidEmbeddedWorkspaceExecutionSurface(surface) { true })

        assertSame(surface, provider.created.single().session.startedSurface)
    }

    @Test
    fun `touch forwards exact typed event`() {
        val provider = FakeSessionProvider()
        val handle = factory(provider).create(target()) {}
        val event = EmbeddedTouchEvent(2, 12.5f, 21.25f, 0.75f, 1234L)

        assertTrue(handle.sendTouch(event))

        assertSame(event, provider.created.single().session.touchEvent)
    }

    @Test
    fun `stop then close delegate once each and preserve terminal callback order`() {
        val provider = FakeSessionProvider()
        val snapshots = mutableListOf<EmbeddedSessionSnapshot>()
        val handle = factory(provider).create(target(), snapshots::add)
        val session = provider.created.single().session

        handle.stop()
        handle.close()

        assertEquals(1, session.stopCalls)
        assertEquals(1, session.closeCalls)
        assertEquals(listOf("stop", "close", "callback:STOPPED"), session.events)
        assertEquals(listOf(EmbeddedSessionPhase.STOPPED), snapshots.map { it.phase })
    }

    @Test
    fun `two factory handles keep independent opaque session identities`() {
        val provider = FakeSessionProvider()
        val factory = factory(provider)

        val first = factory.create(target("one")) {}
        val second = factory.create(target("two")) {}

        assertEquals(2, provider.created.size)
        assertNotEquals(first.sessionId, second.sessionId)
    }

    @Test
    fun `session construction boundary receives only generic target input`() {
        val provider = FakeSessionProvider()
        val target = target()

        factory(provider).create(target) {}

        assertSame(target, provider.created.single().target)
    }

    @Test
    fun `wrong execution surface fails closed before generic session start`() {
        val provider = FakeSessionProvider()
        val handle = factory(provider).create(target()) {}

        try {
            handle.start(object : EmbeddedWorkspaceExecutionSurface {
                override val isValid = true
            })
            fail("Expected wrong surface type to fail closed")
        } catch (_: IllegalArgumentException) {
            // expected
        }

        assertEquals(null, provider.created.single().session.startedSurface)
    }

    @Test
    fun `adapter never releases host owned surface`() {
        val provider = FakeSessionProvider()
        val handle = factory(provider).create(target()) {}
        val surface = surface()
        var valid = true
        val wrapper = AndroidEmbeddedWorkspaceExecutionSurface(surface) { valid }

        handle.start(wrapper)
        handle.stop()
        handle.close()

        assertTrue(wrapper.isValid)
        assertSame(surface, provider.created.single().session.startedSurface)
        valid = false
        assertFalse(wrapper.isValid)
    }

    @Test
    fun `synchronous structured callback is delivered without adapter queueing`() {
        val provider = FakeSessionProvider(syncConnectSnapshot = EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
        val snapshots = mutableListOf<EmbeddedSessionSnapshot>()
        val handle = factory(provider).create(target(), snapshots::add)

        handle.connect()

        assertEquals(listOf(EmbeddedSessionPhase.READY), snapshots.map { it.phase })
        assertEquals(listOf("callback:READY", "connect-return"), provider.created.single().session.events)
    }

    @Test
    fun `notification detach drops late observer events without invoking cleanup`() {
        val provider = FakeSessionProvider()
        val snapshots = mutableListOf<EmbeddedSessionSnapshot>()
        val handle = factory(provider).create(target(), snapshots::add)
        val session = provider.created.single().session
        handle.detachNotifications()
        session.emit(EmbeddedSessionSnapshot(EmbeddedSessionPhase.READY))
        assertEquals(emptyList<EmbeddedSessionSnapshot>(), snapshots)
        assertEquals(0, session.stopCalls)
        assertEquals(0, session.closeCalls)
    }

    private fun factory(provider: FakeSessionProvider) = AndroidEmbeddedWorkspaceSessionFactory(
        applicationContext = context(),
        sessionProvider = provider,
    )

    private fun target(suffix: String = "main") = EmbeddedAppTarget(
        packageName = "com.example.$suffix",
        componentName = "com.example.$suffix.MainActivity",
        geometry = EmbeddedAppGeometry(900, 675, 320),
    )

    private class FakeSessionProvider(
        private val syncConnectSnapshot: EmbeddedSessionSnapshot? = null,
    ) : AndroidEmbeddedAppSessionProvider {
        val created = mutableListOf<Created>()
        private var nextId = 1

        override fun create(
            applicationContext: Context,
            target: EmbeddedAppTarget,
            observer: (EmbeddedSessionSnapshot) -> Unit,
        ): AndroidEmbeddedAppSession {
            val session = FakeSession(
                sessionId = EmbeddedAppSessionId("adapter-session-${nextId++}"),
                observer = observer,
                syncConnectSnapshot = syncConnectSnapshot,
            )
            created += Created(applicationContext, target, session)
            return session
        }
    }

    private data class Created(
        val applicationContext: Context,
        val target: EmbeddedAppTarget,
        val session: FakeSession,
    )

    private class FakeSession(
        override val sessionId: EmbeddedAppSessionId,
        private val observer: (EmbeddedSessionSnapshot) -> Unit,
        private val syncConnectSnapshot: EmbeddedSessionSnapshot?,
    ) : AndroidEmbeddedAppSession {
        val events = mutableListOf<String>()
        var startedSurface: Surface? = null
        var touchEvent: EmbeddedTouchEvent? = null
        var stopCalls = 0
        var closeCalls = 0
        fun emit(snapshot: EmbeddedSessionSnapshot) = observer(snapshot)

        override fun connect() {
            syncConnectSnapshot?.let {
                events += "callback:${it.phase.name}"
                observer(it)
            }
            events += "connect-return"
        }

        override fun startSession(surface: Surface) {
            startedSurface = surface
        }

        override fun sendTouch(event: EmbeddedTouchEvent) {
            touchEvent = event
        }

        override fun stop() {
            stopCalls++
            events += "stop"
        }

        override fun close() {
            closeCalls++
            events += "close"
            val terminal = EmbeddedSessionSnapshot(EmbeddedSessionPhase.STOPPED)
            events += "callback:${terminal.phase.name}"
            observer(terminal)
        }
    }

    private fun context(): Context = allocate(ContextWrapper::class.java)
    private fun surface(): Surface = allocate(Surface::class.java)

    private fun <T> allocate(type: Class<T>): T {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return (field.get(null) as Unsafe).allocateInstance(type) as T
    }
}
