package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

data class EmbeddedCapabilitySnapshot(
    val platformSupported: Boolean,
    val shizukuBinderAvailable: Boolean,
    val shizukuPermissionGranted: Boolean,
)

fun interface EmbeddedCapabilityProbe {
    fun snapshot(): EmbeddedCapabilitySnapshot
}

sealed interface EmbeddedReadinessResult {
    data object Ready : EmbeddedReadinessResult
    data object UnsupportedPlatform : EmbeddedReadinessResult
    data object ShizukuUnavailable : EmbeddedReadinessResult
    data object ShizukuPermissionMissing : EmbeddedReadinessResult
    data object GeometryUnavailable : EmbeddedReadinessResult
    data object RendererNotReady : EmbeddedReadinessResult
}

object EmbeddedWorkspaceReadiness {
    fun evaluate(
        capability: EmbeddedCapabilitySnapshot,
        geometryReady: Boolean,
        rendererReady: Boolean,
        controllerCanStart: Boolean,
    ): EmbeddedReadinessResult = when {
        !capability.platformSupported -> EmbeddedReadinessResult.UnsupportedPlatform
        !capability.shizukuBinderAvailable -> EmbeddedReadinessResult.ShizukuUnavailable
        !capability.shizukuPermissionGranted -> EmbeddedReadinessResult.ShizukuPermissionMissing
        !geometryReady -> EmbeddedReadinessResult.GeometryUnavailable
        !rendererReady || !controllerCanStart -> EmbeddedReadinessResult.RendererNotReady
        else -> EmbeddedReadinessResult.Ready
    }
}
