package com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.apppicker.model.toStableKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SafeCachedLoaderTest {
    @Test fun productionIconCacheIsBoundedToSixtyFourEntries() {
        assertEquals(64, APP_ICON_CACHE_ENTRIES)
    }
    @Test fun cacheHitDoesNotCallLoaderTwice() {
        var calls = 0
        val loader = SafeCachedLoader(4, "fallback") { key: String ->
            calls++
            "icon:$key"
        }

        assertEquals("icon:app", loader.load("app"))
        assertEquals("icon:app", loader.load("app"))
        assertEquals(1, calls)
    }

    @Test fun cacheMissCallsLoaderForEachNewKey() {
        var calls = 0
        val loader = SafeCachedLoader(4, "fallback") { key: String ->
            calls++
            key
        }

        loader.load("first")
        loader.load("second")
        assertEquals(2, calls)
    }

    @Test fun loaderFailureReturnsAndCachesFallback() {
        var calls = 0
        val loader = SafeCachedLoader(4, "fallback") { _: String ->
            calls++
            error("broken")
        }

        assertEquals("fallback", loader.load("app"))
        assertEquals("fallback", loader.load("app"))
        assertEquals(1, calls)
    }

    @Test fun cacheLimitEvictsLeastRecentlyUsedEntry() {
        var calls = 0
        val loader = SafeCachedLoader(2, "fallback") { key: String ->
            calls++
            key
        }

        loader.load("first")
        loader.load("second")
        loader.load("third")
        loader.load("first")
        assertEquals(4, calls)
    }

    @Test fun activitiesInSamePackageUseDifferentCacheKeys() {
        val first = AppIdentity("com.example", "FirstActivity").toStableKey()
        val second = AppIdentity("com.example", "SecondActivity").toStableKey()
        assertNotEquals(first, second)
    }
}
