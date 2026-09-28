package com.trancong.dexworkspacetouch.feature.embeddedapp

import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.RemoteSessionHandle
import com.trancong.dexworkspacetouch.feature.embeddedapp.remote.RemoteSessionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSessionRegistryTest {
    private class Fake : RemoteSessionHandle {
        override var phase = RemoteSessionPhase.RESERVED
        override var hasLiveResources = false
        var touches = 0
        var stops = 0
        override fun touch() { touches++ }
        override fun stop() { stops++; phase = RemoteSessionPhase.STOPPED; hasLiveResources = false }
    }

    @Test fun twoIdsOwnIndependentRuntimesAndRouteTouchById() {
        val made = mutableListOf<Fake>()
        val registry = RemoteSessionRegistry { _ -> Fake().also(made::add) }
        val a = registry.reserve("a")
        val b = registry.reserve("b")
        assertFalse(registry.tryReserve("a"))
        assertSame(a, registry.require("a"))
        assertSame(b, registry.require("b"))
        registry.require("b").touch()
        assertEquals(0, made[0].touches)
        assertEquals(1, made[1].touches)
    }

    @Test fun stopBLeavesAActiveAndRepeatedStopIsIdempotent() {
        val registry = RemoteSessionRegistry { _ -> Fake() }
        val a = registry.reserve("a") as Fake
        val b = registry.reserve("b") as Fake
        a.phase = RemoteSessionPhase.ACTIVE; a.hasLiveResources = true
        b.phase = RemoteSessionPhase.ACTIVE; b.hasLiveResources = true
        assertTrue(registry.stop("b"))
        assertTrue(registry.stop("b"))
        assertEquals(1, b.stops)
        assertEquals(RemoteSessionPhase.ACTIVE, a.phase)
        assertTrue(a.hasLiveResources)
    }

    @Test fun serviceStateCountsPotentiallyLiveEntries() {
        val registry = RemoteSessionRegistry { _ -> Fake() }
        val a = registry.reserve("a") as Fake
        val b = registry.reserve("b") as Fake
        a.phase = RemoteSessionPhase.ACTIVE; a.hasLiveResources = true
        b.phase = RemoteSessionPhase.FAILED; b.hasLiveResources = true
        val state = registry.serviceState()
        assertEquals(1, state.activeSessionCount)
        assertEquals(2, state.liveResourceSessionCount)
    }

    @Test(expected = NoSuchElementException::class)
    fun unknownIdFailsClosed() { RemoteSessionRegistry { _ -> Fake() }.require("missing") }
}
