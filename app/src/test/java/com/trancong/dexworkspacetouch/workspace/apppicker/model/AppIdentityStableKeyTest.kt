package com.trancong.dexworkspacetouch.workspace.apppicker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIdentityStableKeyTest {
    @Test fun packageAndActivityCreateStableStringKey() {
        val identity = AppIdentity("com.example", "com.example.MainActivity")
        assertEquals("com.example#com.example.MainActivity", identity.toStableKey())
    }

    @Test fun nullActivityCreatesStableNonEmptyKey() {
        val key = AppIdentity("com.example", null).toStableKey()
        assertEquals("com.example#", key)
        assertTrue(key.isNotEmpty())
    }

    @Test fun differentActivitiesInSamePackageHaveDifferentKeys() {
        val first = AppIdentity("com.example", "com.example.First").toStableKey()
        val second = AppIdentity("com.example", "com.example.Second").toStableKey()
        assertNotEquals(first, second)
    }

    @Test fun labelDoesNotAffectIdentityKey() {
        val first = InstalledApp("com.example", "com.example.Main", "First label", true)
        val second = InstalledApp("com.example", "com.example.Main", "Second label", true)
        assertEquals(first.identity.toStableKey(), second.identity.toStableKey())
    }

    @Test fun repeatedCallsAreDeterministic() {
        val identity = AppIdentity("com.example", "com.example.Main")
        repeat(10) { assertEquals(identity.toStableKey(), identity.toStableKey()) }
    }

    @Test fun appListProducesUniqueStableKeys() {
        val keys = listOf(
            AppIdentity("com.example", "com.example.First"),
            AppIdentity("com.example", "com.example.Second"),
            AppIdentity("com.other", null),
        ).map(AppIdentity::toStableKey)
        assertEquals(keys.size, keys.distinct().size)
    }
}
