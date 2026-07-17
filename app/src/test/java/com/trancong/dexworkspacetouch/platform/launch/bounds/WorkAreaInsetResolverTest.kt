package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkAreaInsetResolverTest {
    private val display = DiagnosticPixelBounds(0, 0, 1920, 1200)
    private val resolver = WorkAreaInsetResolver()

    @Test
    fun `maximum metrics bounds and insets resolve in same coordinate space`() {
        val result = resolver.resolve(
            display,
            listOf(candidate("maximum", WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS, bottom = 48)),
        )

        assertEquals(1152, result?.workArea?.usableHeight)
        assertEquals(WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS, result?.selectedSource)
    }

    @Test
    fun `root zero does not hide maximum taskbar inset`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate("root", WorkAreaInsetSource.ROOT_SYSTEM_INSETS),
                candidate("maximum", WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS, bottom = 48),
            ),
        )

        assertEquals(48, result?.workArea?.insetBottomPx)
        assertEquals(WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS, result?.selectedSource)
    }

    @Test
    fun `stable inset supplies taskbar when system inset is zero`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate("system", WorkAreaInsetSource.ROOT_SYSTEM_INSETS),
                candidate("stable", WorkAreaInsetSource.ROOT_STABLE_INSETS, bottom = 64),
            ),
        )

        assertEquals(64, result?.workArea?.insetBottomPx)
        assertEquals(WorkAreaInsetSource.ROOT_STABLE_INSETS, result?.selectedSource)
    }

    @Test
    fun `identical system and stable inset is not doubled`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate("system", WorkAreaInsetSource.ROOT_SYSTEM_INSETS, bottom = 64),
                candidate("stable", WorkAreaInsetSource.ROOT_STABLE_INSETS, bottom = 64),
            ),
        )

        assertEquals(64, result?.workArea?.insetBottomPx)
        assertEquals(WorkAreaInsetSource.ROOT_SYSTEM_INSETS, result?.selectedSource)
    }

    @Test
    fun `cutout larger than system wins only on its edge`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate(
                    "system",
                    WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS,
                    top = 20,
                    bottom = 48,
                ),
                candidate("cutout", WorkAreaInsetSource.COMBINED, top = 36),
            ),
        )

        assertEquals(36, result?.workArea?.insetTopPx)
        assertEquals(48, result?.workArea?.insetBottomPx)
        assertEquals(WorkAreaInsetSource.COMBINED, result?.selectedSource)
    }

    @Test
    fun `smaller host-window candidate is rejected for desktop work area`() {
        val hostCandidate = WorkAreaInsetCandidate(
            label = "host",
            source = WorkAreaInsetSource.CURRENT_WINDOW_METRICS,
            referenceBounds = DiagnosticPixelBounds(100, 100, 1100, 800),
            insets = EdgeInsets(bottom = 80),
            coordinateSpace = InsetCoordinateSpace.HOST_WINDOW,
        )
        val result = resolver.resolve(
            display,
            listOf(
                hostCandidate,
                candidate("display", WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS, bottom = 48),
            ),
        )

        assertEquals(48, result?.workArea?.insetBottomPx)
        assertEquals(listOf("display"), result?.acceptedCandidates?.map { it.label })
    }

    @Test
    fun `inset greater than half display is rejected`() {
        val result = resolver.resolve(
            display,
            listOf(candidate("impossible", WorkAreaInsetSource.ROOT_STABLE_INSETS, bottom = 601)),
        )

        assertNull(result)
    }

    @Test
    fun `no display-space candidate returns unavailable`() {
        val result = resolver.resolve(
            display,
            listOf(
                WorkAreaInsetCandidate(
                    label = "visible frame",
                    source = WorkAreaInsetSource.DISPLAY_VISIBLE_FRAME,
                    referenceBounds = DiagnosticPixelBounds(10, 10, 1000, 700),
                    insets = EdgeInsets(),
                    coordinateSpace = InsetCoordinateSpace.HOST_WINDOW,
                ),
            ),
        )

        assertNull(result)
    }

    @Test
    fun `usable area accounts for all four selected edges`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate(
                    "all edges",
                    WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS,
                    left = 10,
                    top = 20,
                    right = 30,
                    bottom = 40,
                ),
            ),
        )

        assertEquals(10, result?.workArea?.originX)
        assertEquals(20, result?.workArea?.originY)
        assertEquals(1880, result?.workArea?.usableWidth)
        assertEquals(1140, result?.workArea?.usableHeight)
        assertEquals(1890, result?.workArea?.usableRight)
        assertEquals(1160, result?.workArea?.usableBottom)
    }

    @Test
    fun `zero root falls back to display metrics delta`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate("root", WorkAreaInsetSource.ROOT_SYSTEM_INSETS),
                candidate("metrics", WorkAreaInsetSource.DISPLAY_METRICS_DELTA, bottom = 48),
            ),
        )

        assertEquals(48, result?.workArea?.insetBottomPx)
        assertEquals(WorkAreaInsetSource.DISPLAY_METRICS_DELTA, result?.selectedSource)
    }

    @Test
    fun `valid root wins deterministically over matching metrics delta`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate("stable", WorkAreaInsetSource.ROOT_STABLE_INSETS, bottom = 48),
                candidate("metrics", WorkAreaInsetSource.DISPLAY_METRICS_DELTA, bottom = 48),
            ),
        )

        assertEquals(48, result?.workArea?.insetBottomPx)
        assertEquals(WorkAreaInsetSource.ROOT_STABLE_INSETS, result?.selectedSource)
    }

    @Test
    fun `valid root wins over conflicting metrics delta`() {
        val result = resolver.resolve(
            display,
            listOf(
                candidate("stable", WorkAreaInsetSource.ROOT_STABLE_INSETS, bottom = 48),
                candidate("metrics", WorkAreaInsetSource.DISPLAY_METRICS_DELTA, bottom = 64),
            ),
        )

        assertEquals(48, result?.workArea?.insetBottomPx)
        assertEquals(WorkAreaInsetSource.ROOT_STABLE_INSETS, result?.selectedSource)
    }

    @Test
    fun `no candidate returns unavailable`() {
        assertNull(resolver.resolve(display, emptyList()))
    }

    private fun candidate(
        label: String,
        source: WorkAreaInsetSource,
        left: Int = 0,
        top: Int = 0,
        right: Int = 0,
        bottom: Int = 0,
    ) = WorkAreaInsetCandidate(
        label = label,
        source = source,
        referenceBounds = display,
        insets = EdgeInsets(left, top, right, bottom),
        coordinateSpace = InsetCoordinateSpace.DISPLAY,
    )
}
