package com.trancong.dexworkspacetouch.platform.launch.bounds

enum class CandidateEvaluation {
    ACCEPTED,
    REJECTED_INVALID_DIMENSIONS,
    REJECTED_COORDINATE_SPACE,
    REJECTED_MATCHES_HOST_WINDOW,
    REJECTED_IMPLAUSIBLE_INSET,
    REJECTED_NO_TASKBAR_EVIDENCE,
    REJECTED_OTHER,
}

data class CandidateDiagnostic(
    val label: String,
    val source: WorkAreaInsetSource,
    val evaluation: CandidateEvaluation,
    val insets: EdgeInsets? = null,
)

data class LegacyWorkAreaDiagnostics(
    val hostDisplayId: Int,
    val realMetricsDisplayId: Int,
    val displayMetricsDisplayId: Int,
    val realDisplayBounds: DiagnosticPixelBounds?,
    val displayMetricsBounds: DiagnosticPixelBounds?,
    val hostWindowBounds: DiagnosticPixelBounds?,
    val candidates: List<CandidateDiagnostic>,
    val selectedSource: WorkAreaInsetSource?,
)

fun legacyMetricDisplayIdsMatch(
    hostDisplayId: Int,
    realMetricsDisplayId: Int,
    displayMetricsDisplayId: Int,
): Boolean =
    hostDisplayId >= 0 &&
        hostDisplayId == realMetricsDisplayId &&
        hostDisplayId == displayMetricsDisplayId
