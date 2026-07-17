package com.trancong.dexworkspacetouch.platform.launch.bounds

data class LegacyDisplayWorkAreaReference(
    val displayId: Int,
    val realDisplayBounds: DiagnosticPixelBounds,
    val density: Float,
    val workArea: DisplayWorkArea,
    val originalSource: WorkAreaInsetSource,
    val captureMode: LegacyReferenceCaptureMode,
    val sequence: Long,
)

enum class LegacyReferenceCaptureMode {
    AUTO_CAPTURED,
    MANUAL,
}

data class LegacyWorkAreaSelection(
    val workArea: DisplayWorkArea,
    val source: WorkAreaInsetSource,
)

object LegacyWorkAreaFallbackResolver {
    fun resolve(
        apiLevel: Int,
        direct: WorkAreaInsetResolution?,
        reference: LegacyDisplayWorkAreaReference?,
    ): LegacyWorkAreaSelection? {
        if (direct != null) {
            return LegacyWorkAreaSelection(direct.workArea, direct.selectedSource)
        }
        if (apiLevel !in LEGACY_API_RANGE || reference == null) return null
        return LegacyWorkAreaSelection(
            reference.workArea,
            WorkAreaInsetSource.LEGACY_FULLSCREEN_REFERENCE,
        )
    }

    private val LEGACY_API_RANGE = 28..29
}

class LegacyDisplayWorkAreaReferenceStore {
    private var reference: LegacyDisplayWorkAreaReference? = null
    private var nextSequence = 1L

    fun saveTrusted(snapshot: DisplayWorkAreaSnapshot, apiLevel: Int): Boolean =
        captureTrusted(snapshot, LegacyReferenceCaptureMode.MANUAL, apiLevel)

    fun autoCaptureTrusted(snapshot: DisplayWorkAreaSnapshot, apiLevel: Int): Boolean =
        captureTrusted(snapshot, LegacyReferenceCaptureMode.AUTO_CAPTURED, apiLevel)

    private fun captureTrusted(
        snapshot: DisplayWorkAreaSnapshot,
        captureMode: LegacyReferenceCaptureMode,
        apiLevel: Int,
    ): Boolean {
        if (apiLevel !in LEGACY_API_RANGE) return false
        if (snapshot.selectedInsetSource !in TRUSTED_SOURCES) return false
        if (!snapshot.workArea.hasMeasuredInset()) return false
        reference = LegacyDisplayWorkAreaReference(
            snapshot.displayId,
            snapshot.rawDisplayBounds,
            snapshot.density,
            snapshot.workArea,
            snapshot.selectedInsetSource,
            captureMode,
            nextSequence++,
        )
        return true
    }

    fun resolve(
        displayId: Int,
        realDisplayBounds: DiagnosticPixelBounds,
        density: Float,
    ): LegacyDisplayWorkAreaReference? {
        val current = reference ?: return null
        if (
            current.displayId != displayId ||
            current.realDisplayBounds != realDisplayBounds ||
            densityChangedSignificantly(current.density, density)
        ) {
            reference = null
            return null
        }
        return current
    }

    fun current(): LegacyDisplayWorkAreaReference? = reference

    fun clear() {
        reference = null
    }

    private fun densityChangedSignificantly(first: Float, second: Float): Boolean {
        if (!first.isFinite() || !second.isFinite() || first <= 0f || second <= 0f) return true
        return kotlin.math.abs(first - second) / first > DENSITY_CHANGE_RATIO
    }

    private fun DisplayWorkArea.hasMeasuredInset() =
        insetLeftPx > 0 || insetTopPx > 0 || insetRightPx > 0 || insetBottomPx > 0

    private companion object {
        val TRUSTED_SOURCES = setOf(
            WorkAreaInsetSource.ROOT_SYSTEM_INSETS,
            WorkAreaInsetSource.ROOT_STABLE_INSETS,
            WorkAreaInsetSource.DISPLAY_METRICS_DELTA,
        )
        val LEGACY_API_RANGE = 28..29
        const val DENSITY_CHANGE_RATIO = 0.02f
    }
}
