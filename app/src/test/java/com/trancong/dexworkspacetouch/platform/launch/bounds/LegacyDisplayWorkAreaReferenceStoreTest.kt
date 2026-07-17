package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDisplayWorkAreaReferenceStoreTest {
    private val bounds = DiagnosticPixelBounds(0, 0, 1920, 1080)
    private val trusted = snapshot()

    @Test
    fun `trusted fullscreen snapshot is stored`() {
        val store = LegacyDisplayWorkAreaReferenceStore()

        assertTrue(store.saveTrusted(trusted, 28))
        assertEquals(trusted.workArea, store.current()?.workArea)
    }

    @Test
    fun `untrusted snapshot is not stored`() {
        val store = LegacyDisplayWorkAreaReferenceStore()

        assertFalse(
            store.saveTrusted(
                trusted.copy(selectedInsetSource = WorkAreaInsetSource.DISPLAY_VISIBLE_FRAME),
                28,
            ),
        )
        assertNull(store.current())
    }

    @Test
    fun `same display and real dimensions resolve reference`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.saveTrusted(trusted, 28)

        assertEquals(trusted.workArea, store.resolve(2, bounds, 1f)?.workArea)
    }

    @Test
    fun `different display rejects and clears reference`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.saveTrusted(trusted, 28)

        assertNull(store.resolve(3, bounds, 1f))
        assertNull(store.current())
    }

    @Test
    fun `different real dimensions reject and clear reference`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.saveTrusted(trusted, 28)

        assertNull(store.resolve(2, DiagnosticPixelBounds(0, 0, 1280, 720), 1f))
        assertNull(store.current())
    }

    @Test
    fun `disconnect clears reference`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.saveTrusted(trusted, 28)

        store.clear()

        assertNull(store.current())
    }

    @Test
    fun `metric display source mismatch is unavailable`() {
        assertFalse(legacyMetricDisplayIdsMatch(2, 2, 3))
        assertFalse(legacyMetricDisplayIdsMatch(2, 3, 2))
        assertTrue(legacyMetricDisplayIdsMatch(2, 2, 2))
    }

    @Test
    fun `direct candidate wins over reference`() {
        val directArea = DisplayWorkArea(1920, 1080, insetBottomPx = 60)
        val direct = WorkAreaInsetResolution(
            directArea,
            WorkAreaInsetSource.ROOT_SYSTEM_INSETS,
            emptyList(),
        )
        val reference = reference()

        val result = LegacyWorkAreaFallbackResolver.resolve(28, direct, reference)

        assertEquals(directArea, result?.workArea)
        assertEquals(WorkAreaInsetSource.ROOT_SYSTEM_INSETS, result?.source)
    }

    @Test
    fun `direct unavailable uses valid reference on legacy API`() {
        val reference = reference()

        val result = LegacyWorkAreaFallbackResolver.resolve(29, null, reference)

        assertEquals(trusted.workArea, result?.workArea)
        assertEquals(WorkAreaInsetSource.LEGACY_FULLSCREEN_REFERENCE, result?.source)
    }

    @Test
    fun `no direct and no reference is unavailable`() {
        assertNull(LegacyWorkAreaFallbackResolver.resolve(28, null, null))
    }

    @Test
    fun `API 30 does not use legacy reference`() {
        val reference = reference()

        assertNull(LegacyWorkAreaFallbackResolver.resolve(30, null, reference))
    }

    @Test
    fun `auto capture trusted direct snapshot records status`() {
        val store = LegacyDisplayWorkAreaReferenceStore()

        assertTrue(store.autoCaptureTrusted(trusted, 28))
        assertEquals(LegacyReferenceCaptureMode.AUTO_CAPTURED, store.current()?.captureMode)
    }

    @Test
    fun `fallback snapshot is not auto captured`() {
        val store = LegacyDisplayWorkAreaReferenceStore()

        assertFalse(
            store.autoCaptureTrusted(
                trusted.copy(
                    selectedInsetSource = WorkAreaInsetSource.LEGACY_FULLSCREEN_REFERENCE,
                ),
                28,
            ),
        )
        assertNull(store.current())
    }

    @Test
    fun `API 30 does not auto capture`() {
        val store = LegacyDisplayWorkAreaReferenceStore()

        assertFalse(store.autoCaptureTrusted(trusted, 30))
        assertFalse(store.saveTrusted(trusted, 30))
        assertNull(store.current())
    }

    @Test
    fun `significant density change rejects and clears reference`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.autoCaptureTrusted(trusted, 28)

        assertNull(store.resolve(2, bounds, 1.1f))
        assertNull(store.current())
    }

    @Test
    fun `small density variation keeps reference`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.autoCaptureTrusted(trusted, 28)

        assertEquals(trusted.workArea, store.resolve(2, bounds, 1.01f)?.workArea)
    }

    @Test
    fun `new store has no process-persistent reference`() {
        val first = LegacyDisplayWorkAreaReferenceStore()
        first.autoCaptureTrusted(trusted, 28)

        assertNull(LegacyDisplayWorkAreaReferenceStore().current())
    }

    @Test
    fun `manual save records manual status separately`() {
        val store = LegacyDisplayWorkAreaReferenceStore()

        assertTrue(store.saveTrusted(trusted, 28))
        assertEquals(LegacyReferenceCaptureMode.MANUAL, store.current()?.captureMode)
    }

    @Test
    fun `later trusted direct snapshot refreshes reference sequence`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.autoCaptureTrusted(trusted, 28)
        val firstSequence = store.current()!!.sequence

        store.autoCaptureTrusted(
            trusted.copy(workArea = DisplayWorkArea(1920, 1080, insetBottomPx = 52)),
            28,
        )

        assertTrue(store.current()!!.sequence > firstSequence)
        assertEquals(52, store.current()?.workArea?.insetBottomPx)
    }

    @Test
    fun `host resize does not affect reference identity`() {
        val store = LegacyDisplayWorkAreaReferenceStore()
        store.autoCaptureTrusted(trusted, 28)

        assertEquals(trusted.workArea, store.resolve(2, bounds, 1f)?.workArea)
    }

    private fun reference() = LegacyDisplayWorkAreaReference(
        displayId = 2,
        realDisplayBounds = bounds,
        density = 1f,
        workArea = trusted.workArea,
        originalSource = WorkAreaInsetSource.ROOT_STABLE_INSETS,
        captureMode = LegacyReferenceCaptureMode.AUTO_CAPTURED,
        sequence = 1,
    )

    private fun snapshot() = DisplayWorkAreaSnapshot(
        displayId = 2,
        workArea = DisplayWorkArea(1920, 1080, insetBottomPx = 48),
        rawDisplayBounds = bounds,
        hostWindowBounds = bounds,
        density = 1f,
        hostWindowMode = HostWindowMode.MAXIMIZED,
        selectedInsetSource = WorkAreaInsetSource.ROOT_STABLE_INSETS,
    )
}
