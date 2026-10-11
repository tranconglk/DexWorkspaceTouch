package com.trancong.dexworkspacetouch.feature.physicalbutton

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalButtonAccessibilityStateTest {
    private val full = "com.trancong.dexworkspacetouch/com.trancong.dexworkspacetouch.feature.physicalbutton.PhysicalButtonAccessibilityService"
    private val short = "com.trancong.dexworkspacetouch/.feature.physicalbutton.PhysicalButtonAccessibilityService"
    private val other = "com.example/com.example.OtherService"
    // Platform parser boundary fixtures; real ComponentName parsing is checked on Android.
    private val parseFixture: (String) -> Boolean? = { entry ->
        when (entry) {
            full, short -> true
            other, "com.trancong.dexworkspacetouch/com.trancong.dexworkspacetouch.OtherService" -> false
            else -> null
        }
    }
    private fun grant(raw: String?, flag: Int = 1) = resolvePhysicalButtonAccessibilityGrant(raw, flag, parseFixture)

    @Test fun fullComponentGrant_enablesGrantWithRuntimeAbsent() {
        val state = PhysicalButtonAccessibilityStateReader({ grant(full) }, { false }).read()
        assertTrue(state.grantEnabled)
        assertFalse(state.runtimeReady)
    }

    @Test fun shortAndFullComponentEntries_enableSameGrant() {
        assertTrue(grant(short))
        assertTrue(grant(full))
    }

    @Test fun otherServiceWithGlobalFlag_doesNotGrantDwt() {
        assertFalse(grant(other))
        assertFalse(grant("com.trancong.dexworkspacetouch/com.trancong.dexworkspacetouch.OtherService"))
    }

    @Test fun validListContainingDwt_grantsOnlyExactComponent() {
        assertTrue(grant("$other:$short"))
    }

    @Test fun missingNullOrMalformedList_deniesGrant() {
        for (raw in listOf(null, "", " ", "broken", "com.trancong.dexworkspacetouch", "$full:broken", ":$full", "$full:")) {
            assertFalse("raw=$raw", grant(raw))
        }
    }

    @Test fun globalFlagZeroOrUnexpectedValue_deniesGrant() {
        assertFalse(grant(full, 0))
        assertFalse(grant(full, 2))
    }

    @Test fun grantRevoked_doesNotUseReadyRuntimeAsFallback() {
        var raw: String? = full
        val reader = PhysicalButtonAccessibilityStateReader({ grant(raw) }, { true })
        assertTrue(reader.read().grantEnabled)
        raw = other
        assertFalse(reader.read().grantEnabled)
    }

    @Test fun grantReadFailure_deniesGrantEvenWithRuntimeReady() {
        val state = PhysicalButtonAccessibilityStateReader({ throw SecurityException("denied") }, { true }).read()
        assertFalse(state.grantEnabled)
    }

    @Test fun parserFailure_deniesGrant() {
        assertFalse(resolvePhysicalButtonAccessibilityGrant(full, 1) { throw IllegalArgumentException("malformed") })
    }

    @Test fun runtimeReadFailure_doesNotRemoveGrant() {
        val state = PhysicalButtonAccessibilityStateReader({ grant(full) }, { throw IllegalStateException("unavailable") }).read()
        assertTrue(state.grantEnabled)
        assertFalse(state.runtimeReady)
    }

    @Test fun resumeAfterSettings_refreshesFalseToTrue() {
        var raw: String? = null
        val reader = PhysicalButtonAccessibilityStateReader({ grant(raw) }, { false })
        var state = reader.read()
        val observer = PhysicalButtonAccessibilityStateObserver(reader::read) { state = it }
        assertFalse(state.grantEnabled)
        raw = full
        observer.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        assertTrue(state.grantEnabled)
        assertFalse(state.runtimeReady)
    }

    @Test fun resumeAfterSettings_refreshesTrueToFalseWithReadyRuntime() {
        var raw: String? = full
        val reader = PhysicalButtonAccessibilityStateReader({ grant(raw) }, { true })
        var state = reader.read()
        val observer = PhysicalButtonAccessibilityStateObserver(reader::read) { state = it }
        raw = null
        observer.onStateChanged(owner, Lifecycle.Event.ON_RESUME)
        assertFalse(state.grantEnabled)
    }

    @Test fun settingChange_refreshesWithoutWaitingForResume() {
        var raw: String? = full
        val reader = PhysicalButtonAccessibilityStateReader({ grant(raw) }, { false })
        var state = reader.read()
        val observer = PhysicalButtonAccessibilityStateObserver(reader::read) { state = it }
        raw = null
        observer.refresh()
        assertEquals(PhysicalButtonAccessibilityState(false, false), state)
    }

    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = error("Observer does not access lifecycle")
    }
}
