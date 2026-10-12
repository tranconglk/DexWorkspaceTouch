package com.trancong.dexworkspacetouch.workspace.close

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloseRpcLaneTest {
    @Test fun timeoutDoesNotReopenAdmissionWhileTransactionIsInFlight() = runBlocking {
        val lane = CloseRpcLane(); val entered = CountDownLatch(1); val release = CountDownLatch(1); val cleaned = CountDownLatch(1)
        try {
            val result = withTimeoutOrNull(100) { lane.call { entered.countDown(); release.await(2, TimeUnit.SECONDS); 1 } }
            assertNull(result)
            assertEquals(0L, entered.count)
            assertFalse(lane.quiescent)
            try { lane.call { fail("Overlapping mutation"); 2 }; fail() }
            catch (e: CloseBackendException) { assertEquals("EXECUTOR_IN_FLIGHT", e.reason) }
            lane.afterQuiescent { cleaned.countDown() }
        } finally { release.countDown() }
        assertTrue(cleaned.await(2, TimeUnit.SECONDS))
    }
    @Test fun profilesRequireBothSdkAndExactQualifiedBuild() {
        assertEquals(CloseAdapterProfile.NOTE9_API29, CloseAdapterProfile.select(29, "samsung/crownltexx/crownlte:10/QP1A.190711.020/N960FXXU9FUK2:user/release-keys"))
        assertEquals(CloseAdapterProfile.S23_API36, CloseAdapterProfile.select(36, "samsung/dm3qxxx/dm3q:16/BP4A.251205.006/S918BXXSAFZI1:user/release-keys"))
        for ((sdk, fp) in listOf(36 to "unknown", 37 to CloseAdapterProfile.S23_API36.fingerprint)) {
            try { CloseAdapterProfile.select(sdk, fp); fail() } catch (e: CloseBackendException) { assertEquals("UNSUPPORTED_ADAPTER", e.reason) }
        }
    }
}
