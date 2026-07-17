package com.trancong.dexworkspacetouch.platform.launch.android

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowInsets
import androidx.annotation.RequiresApi
import com.trancong.dexworkspacetouch.platform.launch.bounds.DiagnosticPixelBounds
import com.trancong.dexworkspacetouch.platform.launch.bounds.CandidateDiagnostic
import com.trancong.dexworkspacetouch.platform.launch.bounds.CandidateEvaluation
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaProvider
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayWorkAreaSnapshot
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayMetricsDeltaEvaluation
import com.trancong.dexworkspacetouch.platform.launch.bounds.DisplayMetricsDeltaEvaluator
import com.trancong.dexworkspacetouch.platform.launch.bounds.EdgeInsets
import com.trancong.dexworkspacetouch.platform.launch.bounds.HostWindowMode
import com.trancong.dexworkspacetouch.platform.launch.bounds.InsetCoordinateSpace
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyDisplayWorkAreaReferenceStore
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyWorkAreaDiagnostics
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyWorkAreaFallbackResolver
import com.trancong.dexworkspacetouch.platform.launch.bounds.legacyMetricDisplayIdsMatch
import com.trancong.dexworkspacetouch.platform.launch.bounds.WorkAreaInsetCandidate
import com.trancong.dexworkspacetouch.platform.launch.bounds.WorkAreaInsetResolver
import com.trancong.dexworkspacetouch.platform.launch.bounds.WorkAreaInsetSource

class ActivityDisplayWorkAreaProvider(
    private val activity: Activity,
    private val insetResolver: WorkAreaInsetResolver = WorkAreaInsetResolver(),
    private val displayMetricsDeltaEvaluator: DisplayMetricsDeltaEvaluator =
        DisplayMetricsDeltaEvaluator(),
    private val legacyReferenceStore: LegacyDisplayWorkAreaReferenceStore =
        LegacyDisplayWorkAreaReferenceStore(),
) : DisplayWorkAreaProvider {
    var lastLegacyDiagnostics: LegacyWorkAreaDiagnostics? = null
        private set

    override fun getSnapshot(): DisplayWorkAreaSnapshot? {
        if (activity.isFinishing || activity.isDestroyed) return null
        val decorView = activity.window.decorView
        if (!decorView.isAttachedToWindow) return null

        val hostDisplay = currentDisplay() ?: run {
            legacyReferenceStore.clear()
            return null
        }
        if (hostDisplay.displayId == Display.DEFAULT_DISPLAY) {
            legacyReferenceStore.clear()
            return null
        }
        val displayManager = activity.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val activeDisplay = displayManager.getDisplay(hostDisplay.displayId) ?: run {
            legacyReferenceStore.clear()
            return null
        }
        if (activeDisplay.state == Display.STATE_OFF) {
            legacyReferenceStore.clear()
            return null
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            snapshotFromWindowMetrics(activeDisplay)
        } else {
            snapshotFromLegacyDisplay(activeDisplay)
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplay(): Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        activity.windowManager.defaultDisplay
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun snapshotFromWindowMetrics(display: Display): DisplayWorkAreaSnapshot? {
        val maximumMetrics = activity.windowManager.maximumWindowMetrics
        val currentMetrics = activity.windowManager.currentWindowMetrics
        val displayBounds = maximumMetrics.bounds.toDiagnosticBoundsOrNull() ?: return null
        val hostBounds = currentMetrics.bounds.toDiagnosticBoundsOrNull() ?: return null
        val maximumInsets = maximumMetrics.windowInsets
        val currentInsets = currentMetrics.windowInsets
        val rootInsets = activity.window.decorView.rootWindowInsets

        val candidates = buildList {
            add(
                displayCandidate(
                    "maximum.systemBars.visible",
                    WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS,
                    displayBounds,
                    maximumInsets.getInsets(WindowInsets.Type.systemBars()).toEdgeInsets(),
                ),
            )
            add(
                displayCandidate(
                    "maximum.systemBars.stable",
                    WorkAreaInsetSource.MAXIMUM_WINDOW_METRICS,
                    displayBounds,
                    maximumInsets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars(),
                    ).toEdgeInsets(),
                ),
            )
            add(
                displayCandidate(
                    "maximum.displayCutout",
                    WorkAreaInsetSource.COMBINED,
                    displayBounds,
                    maximumInsets.getInsets(WindowInsets.Type.displayCutout()).toEdgeInsets(),
                ),
            )
            add(
                hostCandidate(
                    "current.systemBars",
                    WorkAreaInsetSource.CURRENT_WINDOW_METRICS,
                    hostBounds,
                    currentInsets.getInsets(WindowInsets.Type.systemBars()).toEdgeInsets(),
                ),
            )
            add(
                hostCandidate(
                    "current.displayCutout",
                    WorkAreaInsetSource.CURRENT_WINDOW_METRICS,
                    hostBounds,
                    currentInsets.getInsets(WindowInsets.Type.displayCutout()).toEdgeInsets(),
                ),
            )
            if (rootInsets != null) {
                add(
                    hostCandidate(
                        "root.systemBars",
                        WorkAreaInsetSource.ROOT_SYSTEM_INSETS,
                        hostBounds,
                        rootInsets.getInsets(WindowInsets.Type.systemBars()).toEdgeInsets(),
                    ),
                )
                add(
                    hostCandidate(
                        "root.displayCutout",
                        WorkAreaInsetSource.COMBINED,
                        hostBounds,
                        rootInsets.getInsets(WindowInsets.Type.displayCutout()).toEdgeInsets(),
                    ),
                )
            }
        }
        val resolution = insetResolver.resolve(displayBounds, candidates) ?: return null
        val density = displayDensity(display) ?: return null

        return DisplayWorkAreaSnapshot(
            displayId = display.displayId,
            workArea = resolution.workArea,
            rawDisplayBounds = displayBounds,
            hostWindowBounds = hostBounds,
            density = density,
            hostWindowMode = if (hostBounds == displayBounds) {
                HostWindowMode.MAXIMIZED
            } else {
                HostWindowMode.WINDOWED
            },
            insetCandidates = candidates,
            selectedInsetSource = resolution.selectedSource,
        )
    }

    @Suppress("DEPRECATION")
    private fun snapshotFromLegacyDisplay(display: Display): DisplayWorkAreaSnapshot? {
        val hostDisplayId = currentDisplay()?.displayId ?: return null
        val realMetricsDisplayId = display.displayId
        val displayMetricsDisplayId = display.displayId
        if (!legacyMetricDisplayIdsMatch(
                hostDisplayId,
                realMetricsDisplayId,
                displayMetricsDisplayId,
            )
        ) {
            legacyReferenceStore.clear()
            return null
        }
        val rootInsets = activity.window.decorView.rootWindowInsets
        val realMetrics = DisplayMetrics()
        val displayMetrics = DisplayMetrics()
        display.getRealMetrics(realMetrics)
        display.getMetrics(displayMetrics)
        if (realMetrics.widthPixels <= 0 || realMetrics.heightPixels <= 0) {
            lastLegacyDiagnostics = LegacyWorkAreaDiagnostics(
                hostDisplayId,
                realMetricsDisplayId,
                displayMetricsDisplayId,
                null,
                null,
                hostWindowBoundsOrNull(),
                listOf(
                    CandidateDiagnostic(
                        "display.metrics.delta",
                        WorkAreaInsetSource.DISPLAY_METRICS_DELTA,
                        CandidateEvaluation.REJECTED_INVALID_DIMENSIONS,
                    ),
                ),
                null,
            )
            return null
        }

        val displayBounds = DiagnosticPixelBounds(
            0,
            0,
            realMetrics.widthPixels,
            realMetrics.heightPixels,
        )
        val hostBounds = hostWindowBoundsOrNull() ?: return null
        val hostWindowMode = if (hostBounds == displayBounds) {
            HostWindowMode.MAXIMIZED
        } else {
            HostWindowMode.WINDOWED
        }
        val visibleFrame = Rect().also(activity.window.decorView::getWindowVisibleDisplayFrame)
        val cutout = rootInsets?.displayCutout
        val rootCandidates = if (rootInsets == null) emptyList() else listOf(
            displayCandidate(
                "root.systemWindowInsets",
                WorkAreaInsetSource.ROOT_SYSTEM_INSETS,
                displayBounds,
                EdgeInsets(
                    rootInsets.systemWindowInsetLeft,
                    rootInsets.systemWindowInsetTop,
                    rootInsets.systemWindowInsetRight,
                    rootInsets.systemWindowInsetBottom,
                ),
            ),
            displayCandidate(
                "root.stableInsets",
                WorkAreaInsetSource.ROOT_STABLE_INSETS,
                displayBounds,
                EdgeInsets(
                    rootInsets.stableInsetLeft,
                    rootInsets.stableInsetTop,
                    rootInsets.stableInsetRight,
                    rootInsets.stableInsetBottom,
                ),
            ),
            displayCandidate(
                "root.displayCutout",
                WorkAreaInsetSource.COMBINED,
                displayBounds,
                EdgeInsets(
                    cutout?.safeInsetLeft ?: 0,
                    cutout?.safeInsetTop ?: 0,
                    cutout?.safeInsetRight ?: 0,
                    cutout?.safeInsetBottom ?: 0,
                ),
            ),
        )
        val metricsEvaluation = displayMetricsDeltaEvaluator.evaluate(
            realDisplayBounds = displayBounds,
            metricsWidthPx = displayMetrics.widthPixels,
            metricsHeightPx = displayMetrics.heightPixels,
            hostWindowBounds = hostBounds,
            hostWindowMode = hostWindowMode,
        )
        val hasTrustworthyRootInset = rootCandidates.any { candidate ->
            candidate.insets.run { left > 0 || top > 0 || right > 0 || bottom > 0 }
        }
        val candidates = buildList {
            addAll(rootCandidates)
            if (displayMetrics.widthPixels > 0 && displayMetrics.heightPixels > 0) {
                add(
                    hostCandidate(
                        "display.metrics",
                        WorkAreaInsetSource.DISPLAY_VISIBLE_FRAME,
                        DiagnosticPixelBounds(
                            0,
                            0,
                            displayMetrics.widthPixels,
                            displayMetrics.heightPixels,
                        ),
                        EdgeInsets(),
                    ),
                )
            }
            if (metricsEvaluation is DisplayMetricsDeltaEvaluation.Available) {
                add(metricsEvaluation.candidate)
            }
            visibleFrame.toDiagnosticBoundsOrNull()?.let { bounds ->
                add(
                    hostCandidate(
                        "decor.visibleDisplayFrame",
                        WorkAreaInsetSource.DISPLAY_VISIBLE_FRAME,
                        bounds,
                        EdgeInsets(),
                    ),
                )
            }
        }
        val density = displayDensity(display) ?: return null
        val directAllowed = hasTrustworthyRootInset ||
            metricsEvaluation == DisplayMetricsDeltaEvaluation.NoDelta ||
            metricsEvaluation is DisplayMetricsDeltaEvaluation.Available
        val resolution = if (directAllowed) {
            insetResolver.resolve(displayBounds, candidates)
        } else {
            null
        }
        val candidateDiagnostics = buildList {
            if (rootInsets == null) {
                add(
                    CandidateDiagnostic(
                        "root.systemWindowInsets",
                        WorkAreaInsetSource.ROOT_SYSTEM_INSETS,
                        CandidateEvaluation.REJECTED_OTHER,
                    ),
                )
                add(
                    CandidateDiagnostic(
                        "root.stableInsets",
                        WorkAreaInsetSource.ROOT_STABLE_INSETS,
                        CandidateEvaluation.REJECTED_OTHER,
                    ),
                )
                add(
                    CandidateDiagnostic(
                        "root.displayCutout",
                        WorkAreaInsetSource.COMBINED,
                        CandidateEvaluation.REJECTED_OTHER,
                    ),
                )
            }
            rootCandidates.forEach { candidate ->
                add(
                    CandidateDiagnostic(
                        candidate.label,
                        candidate.source,
                        if (candidate.insets.hasAny()) {
                            CandidateEvaluation.ACCEPTED
                        } else {
                            CandidateEvaluation.REJECTED_NO_TASKBAR_EVIDENCE
                        },
                        candidate.insets,
                    ),
                )
            }
            add(
                CandidateDiagnostic(
                    DisplayMetricsDeltaEvaluator.DISPLAY_METRICS_LABEL,
                    WorkAreaInsetSource.DISPLAY_METRICS_DELTA,
                    when (metricsEvaluation) {
                        is DisplayMetricsDeltaEvaluation.Available -> CandidateEvaluation.ACCEPTED
                        DisplayMetricsDeltaEvaluation.HostWindowSized ->
                            CandidateEvaluation.REJECTED_MATCHES_HOST_WINDOW
                        DisplayMetricsDeltaEvaluation.Invalid ->
                            CandidateEvaluation.REJECTED_INVALID_DIMENSIONS
                        DisplayMetricsDeltaEvaluation.NoDelta ->
                            CandidateEvaluation.REJECTED_NO_TASKBAR_EVIDENCE
                    },
                    (metricsEvaluation as? DisplayMetricsDeltaEvaluation.Available)
                        ?.candidate
                        ?.insets,
                ),
            )
        }

        if (resolution != null) {
            legacyReferenceStore.autoCaptureTrusted(
                DisplayWorkAreaSnapshot(
                    displayId = display.displayId,
                    workArea = resolution.workArea,
                    rawDisplayBounds = displayBounds,
                    hostWindowBounds = hostBounds,
                    density = density,
                    hostWindowMode = hostWindowMode,
                    insetCandidates = candidates,
                    selectedInsetSource = resolution.selectedSource,
                ),
                Build.VERSION.SDK_INT,
            )
        }
        val reference = legacyReferenceStore.resolve(
            display.displayId,
            displayBounds,
            density,
        )
        val selection = LegacyWorkAreaFallbackResolver.resolve(
            Build.VERSION.SDK_INT,
            resolution,
            reference,
        )
        val usedReference = selection?.source == WorkAreaInsetSource.LEGACY_FULLSCREEN_REFERENCE
        lastLegacyDiagnostics = LegacyWorkAreaDiagnostics(
            hostDisplayId,
            realMetricsDisplayId,
            displayMetricsDisplayId,
            displayBounds,
            displayMetrics.toBoundsOrNull(),
            hostBounds,
            candidateDiagnostics + if (resolution == null) {
                listOf(
                    CandidateDiagnostic(
                        "legacy.fullscreen.reference",
                        WorkAreaInsetSource.LEGACY_FULLSCREEN_REFERENCE,
                        if (usedReference) {
                            CandidateEvaluation.ACCEPTED
                        } else {
                            CandidateEvaluation.REJECTED_OTHER
                        },
                        reference?.workArea?.let {
                            EdgeInsets(
                                it.insetLeftPx,
                                it.insetTopPx,
                                it.insetRightPx,
                                it.insetBottomPx,
                            )
                        },
                    ),
                )
            } else {
                emptyList()
            },
            selection?.source,
        )
        if (selection == null) return null

        return DisplayWorkAreaSnapshot(
            displayId = display.displayId,
            workArea = selection.workArea,
            rawDisplayBounds = displayBounds,
            hostWindowBounds = hostBounds,
            density = density,
            hostWindowMode = hostWindowMode,
            insetCandidates = candidates,
            selectedInsetSource = selection.source,
        )
    }

    private fun EdgeInsets.hasAny() = left > 0 || top > 0 || right > 0 || bottom > 0

    private fun DisplayMetrics.toBoundsOrNull(): DiagnosticPixelBounds? =
        if (widthPixels > 0 && heightPixels > 0) {
            DiagnosticPixelBounds(0, 0, widthPixels, heightPixels)
        } else {
            null
        }

    private fun displayDensity(display: Display): Float? {
        val density = activity.createDisplayContext(display).resources.displayMetrics.density
        return density.takeIf { it.isFinite() && it > 0f }
    }

    private fun hostWindowBoundsOrNull(): DiagnosticPixelBounds? {
        val decorView = activity.window.decorView
        if (decorView.width <= 0 || decorView.height <= 0) return null
        val location = IntArray(2)
        decorView.getLocationOnScreen(location)
        return DiagnosticPixelBounds(
            left = location[0],
            top = location[1],
            right = location[0] + decorView.width,
            bottom = location[1] + decorView.height,
        )
    }

    private fun displayCandidate(
        label: String,
        source: WorkAreaInsetSource,
        bounds: DiagnosticPixelBounds,
        insets: EdgeInsets,
    ) = WorkAreaInsetCandidate(label, source, bounds, insets, InsetCoordinateSpace.DISPLAY)

    private fun hostCandidate(
        label: String,
        source: WorkAreaInsetSource,
        bounds: DiagnosticPixelBounds,
        insets: EdgeInsets,
    ) = WorkAreaInsetCandidate(label, source, bounds, insets, InsetCoordinateSpace.HOST_WINDOW)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun android.graphics.Insets.toEdgeInsets() = EdgeInsets(left, top, right, bottom)

    private fun Rect.toDiagnosticBoundsOrNull(): DiagnosticPixelBounds? =
        if (width() > 0 && height() > 0) {
            DiagnosticPixelBounds(left, top, right, bottom)
        } else {
            null
        }
}
