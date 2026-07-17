package com.trancong.dexworkspacetouch.platform.launch.bounds

data class EdgeInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) {
            "insets must not be negative"
        }
    }
}

enum class WorkAreaInsetSource {
    MAXIMUM_WINDOW_METRICS,
    CURRENT_WINDOW_METRICS,
    ROOT_SYSTEM_INSETS,
    ROOT_STABLE_INSETS,
    DISPLAY_METRICS_DELTA,
    LEGACY_FULLSCREEN_REFERENCE,
    DISPLAY_VISIBLE_FRAME,
    COMBINED,
}

enum class InsetCoordinateSpace {
    DISPLAY,
    HOST_WINDOW,
}

data class WorkAreaInsetCandidate(
    val label: String,
    val source: WorkAreaInsetSource,
    val referenceBounds: DiagnosticPixelBounds,
    val insets: EdgeInsets,
    val coordinateSpace: InsetCoordinateSpace,
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
    }

    fun workAreaOrNull(displayBounds: DiagnosticPixelBounds): DisplayWorkArea? {
        if (coordinateSpace != InsetCoordinateSpace.DISPLAY) return null
        if (referenceBounds != displayBounds) return null
        return runCatching {
            DisplayWorkArea(
                widthPx = displayBounds.width,
                heightPx = displayBounds.height,
                insetLeftPx = insets.left,
                insetTopPx = insets.top,
                insetRightPx = insets.right,
                insetBottomPx = insets.bottom,
            )
        }.getOrNull()
    }
}

data class WorkAreaInsetResolution(
    val workArea: DisplayWorkArea,
    val selectedSource: WorkAreaInsetSource,
    val acceptedCandidates: List<WorkAreaInsetCandidate>,
)

class WorkAreaInsetResolver {
    fun resolve(
        displayBounds: DiagnosticPixelBounds,
        candidates: List<WorkAreaInsetCandidate>,
    ): WorkAreaInsetResolution? {
        val accepted = candidates.filter { candidate ->
            candidate.workAreaOrNull(displayBounds) != null &&
                candidate.insets.left <= displayBounds.width / 2 &&
                candidate.insets.right <= displayBounds.width / 2 &&
                candidate.insets.top <= displayBounds.height / 2 &&
                candidate.insets.bottom <= displayBounds.height / 2
        }
        if (accepted.isEmpty()) return null

        val selectedSources = linkedSetOf<WorkAreaInsetSource>()
        val left = selectEdge(accepted, selectedSources) { it.left }
        val top = selectEdge(accepted, selectedSources) { it.top }
        val right = selectEdge(accepted, selectedSources) { it.right }
        val bottom = selectEdge(accepted, selectedSources) { it.bottom }
        val workArea = runCatching {
            DisplayWorkArea(
                widthPx = displayBounds.width,
                heightPx = displayBounds.height,
                insetLeftPx = left,
                insetTopPx = top,
                insetRightPx = right,
                insetBottomPx = bottom,
            )
        }.getOrNull() ?: return null

        val selectedSource = when {
            selectedSources.size == 1 -> selectedSources.single()
            selectedSources.isEmpty() -> accepted.first().source
            else -> WorkAreaInsetSource.COMBINED
        }
        return WorkAreaInsetResolution(workArea, selectedSource, accepted)
    }

    private fun selectEdge(
        candidates: List<WorkAreaInsetCandidate>,
        selectedSources: MutableSet<WorkAreaInsetSource>,
        value: (EdgeInsets) -> Int,
    ): Int {
        val primaryCandidates = candidates.filter {
            it.source != WorkAreaInsetSource.DISPLAY_METRICS_DELTA
        }
        val primary = primaryCandidates.maxByOrNull { value(it.insets) }
        val selected = if (primary != null && value(primary.insets) > 0) {
            primary
        } else {
            candidates
                .filter { it.source == WorkAreaInsetSource.DISPLAY_METRICS_DELTA }
                .maxByOrNull { value(it.insets) }
                ?: primary
                ?: return 0
        }
        val selectedValue = value(selected.insets)
        if (selectedValue > 0) selectedSources += selected.source
        return selectedValue
    }
}
