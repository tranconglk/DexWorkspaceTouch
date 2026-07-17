package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayMetricsDeltaEvaluatorTest {
    private val evaluator = DisplayMetricsDeltaEvaluator()
    private val real = DiagnosticPixelBounds(0, 0, 1920, 1080)

    @Test
    fun `real 1920 by 1080 and metrics 1920 by 1032 yields bottom 48`() {
        val result = evaluate(1920, 1032)

        val candidate = (result as DisplayMetricsDeltaEvaluation.Available).candidate
        assertEquals(EdgeInsets(bottom = 48), candidate.insets)
        assertEquals(WorkAreaInsetSource.DISPLAY_METRICS_DELTA, candidate.source)
    }

    @Test
    fun `equal real and metrics does not invent taskbar inset`() {
        assertEquals(DisplayMetricsDeltaEvaluation.NoDelta, evaluate(1920, 1080))
    }

    @Test
    fun `metrics larger than real is rejected`() {
        assertEquals(DisplayMetricsDeltaEvaluation.Invalid, evaluate(1921, 1080))
    }

    @Test
    fun `metrics matching a windowed host are rejected`() {
        val result = evaluator.evaluate(
            real,
            metricsWidthPx = 1000,
            metricsHeightPx = 700,
            hostWindowBounds = DiagnosticPixelBounds(100, 100, 1100, 800),
            hostWindowMode = HostWindowMode.WINDOWED,
        )

        assertEquals(DisplayMetricsDeltaEvaluation.HostWindowSized, result)
    }

    @Test
    fun `metrics nearly matching a windowed host are rejected`() {
        val result = evaluator.evaluate(
            real,
            metricsWidthPx = 1010,
            metricsHeightPx = 710,
            hostWindowBounds = DiagnosticPixelBounds(100, 100, 1100, 800),
            hostWindowMode = HostWindowMode.WINDOWED,
        )

        assertEquals(DisplayMetricsDeltaEvaluation.HostWindowSized, result)
    }

    @Test
    fun `stable desktop metrics different from windowed host are accepted`() {
        val result = evaluator.evaluate(
            real,
            metricsWidthPx = 1920,
            metricsHeightPx = 1032,
            hostWindowBounds = DiagnosticPixelBounds(100, 100, 1100, 800),
            hostWindowMode = HostWindowMode.WINDOWED,
        )

        assertTrue(result is DisplayMetricsDeltaEvaluation.Available)
    }

    @Test
    fun `only one dimension matching host does not cause rejection`() {
        val result = evaluator.evaluate(
            real,
            metricsWidthPx = 1000,
            metricsHeightPx = 1032,
            hostWindowBounds = DiagnosticPixelBounds(100, 100, 1100, 800),
            hostWindowMode = HostWindowMode.WINDOWED,
        )

        assertTrue(result is DisplayMetricsDeltaEvaluation.Available)
    }

    @Test
    fun `metrics larger than host and inside real are accepted`() {
        val result = evaluator.evaluate(
            real,
            metricsWidthPx = 1920,
            metricsHeightPx = 1032,
            hostWindowBounds = DiagnosticPixelBounds(100, 100, 1100, 800),
            hostWindowMode = HostWindowMode.WINDOWED,
        )

        assertTrue(result is DisplayMetricsDeltaEvaluation.Available)
    }

    @Test
    fun `delta larger than half the display is rejected`() {
        assertEquals(DisplayMetricsDeltaEvaluation.Invalid, evaluate(900, 500))
    }

    private fun evaluate(width: Int, height: Int) = evaluator.evaluate(
        realDisplayBounds = real,
        metricsWidthPx = width,
        metricsHeightPx = height,
        hostWindowBounds = real,
        hostWindowMode = HostWindowMode.MAXIMIZED,
    )
}
