package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class ProductAdmissionUiQueryTest {
    @Test fun uiQueriesReturnFailClosedBeforeRepairReleasesItsLock() {
        val gate = EmbeddedProductRunGate()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val queriesReturned = CountDownLatch(2)
        val canEnter = AtomicReference<Boolean>()
        val created = AtomicReference<Any>()
        val factoryCalled = AtomicBoolean(false)
        val repair = Thread { gate.tryDispatchClassic { entered.countDown(); release.await() } }
        val query = Thread { try { canEnter.set(gate.canEnterEmbedded()) } finally { queriesReturned.countDown() } }
        val factory = Thread { try {
            created.set(gate.createExecutionIfIdle { factoryCalled.set(true); Any() })
        } finally { queriesReturned.countDown() } }
        try {
            repair.start()
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            query.start(); factory.start()
            assertTrue("UI callers must return while Repair still owns the lock", queriesReturned.await(1, TimeUnit.SECONDS))
            assertEquals(false, canEnter.get())
            assertNull(created.get())
            assertFalse(factoryCalled.get())
        } finally {
            release.countDown()
            repair.join(2000); query.join(2000); factory.join(2000)
        }
        assertTrue(gate.canEnterEmbedded())
        assertNotNull(gate.createExecutionIfIdle { factoryCalled.set(true); Any() })
        assertTrue(factoryCalled.get())
    }
}
