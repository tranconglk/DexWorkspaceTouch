package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceGeometrySnapshot
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceLayoutLoadResult
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.EmbeddedWorkspaceLayoutLoader
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.GeometrySnapshotResult
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedGeometryPolicy
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import kotlin.math.max
import kotlin.math.min

sealed interface EmbeddedEligibilityFailure {
    data class MissingWorkspace(val workspaceId: String) : EmbeddedEligibilityFailure
    data class LaunchNotReady(val reason: LaunchReadiness) : EmbeddedEligibilityFailure
    data class UnsupportedEmbeddedItemCount(val count: Int) : EmbeddedEligibilityFailure
    data class DuplicateTarget(val packageName: String, val componentName: String) : EmbeddedEligibilityFailure
    data object UnsupportedLayout : EmbeddedEligibilityFailure
    data class GeometryUnavailable(val sourceCellId: String) : EmbeddedEligibilityFailure
}

sealed interface EmbeddedEligibilityResult {
    data class Ready(val snapshot: EmbeddedWorkspaceGeometrySnapshot) : EmbeddedEligibilityResult
    data class Rejected(val reason: EmbeddedEligibilityFailure) : EmbeddedEligibilityResult
}

class EmbeddedWorkspaceProductLoader(private val layoutLoader: EmbeddedWorkspaceLayoutLoader) {
    suspend fun loadEligible(workspaceId: String, geometryPolicy: EmbeddedGeometryPolicy): EmbeddedEligibilityResult {
        val plan = when (val loaded = layoutLoader.load(workspaceId)) {
            is EmbeddedWorkspaceLayoutLoadResult.Ready -> loaded.plan
            is EmbeddedWorkspaceLayoutLoadResult.MissingWorkspace -> return EmbeddedEligibilityResult.Rejected(
                EmbeddedEligibilityFailure.MissingWorkspace(loaded.workspaceId),
            )
            is EmbeddedWorkspaceLayoutLoadResult.LaunchNotReady -> return EmbeddedEligibilityResult.Rejected(
                EmbeddedEligibilityFailure.LaunchNotReady(loaded.reason),
            )
        }
        EmbeddedWorkspaceEligibility.evaluate(plan)?.let { return EmbeddedEligibilityResult.Rejected(it) }
        return when (val geometry = EmbeddedWorkspaceGeometrySnapshot.resolve(plan, geometryPolicy)) {
            is GeometrySnapshotResult.Ready -> EmbeddedEligibilityResult.Ready(geometry.snapshot)
            is GeometrySnapshotResult.GeometryRejected -> EmbeddedEligibilityResult.Rejected(
                EmbeddedEligibilityFailure.GeometryUnavailable(geometry.sourceCellId),
            )
        }
    }
}

object EmbeddedWorkspaceEligibility {
    fun evaluate(plan: EmbeddedWorkspacePlan): EmbeddedEligibilityFailure? {
        if (plan.items.size !in 1..2) {
            return EmbeddedEligibilityFailure.UnsupportedEmbeddedItemCount(plan.items.size)
        }
        val duplicate = plan.items.groupBy { it.packageName to it.componentName }
            .entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) return EmbeddedEligibilityFailure.DuplicateTarget(
            duplicate.key.first, duplicate.key.second,
        )
        val bounds = plan.items.map { it.normalizedBounds }
        if (bounds.any { b -> b.left !in 0f..1f || b.top !in 0f..1f ||
                b.right !in 0f..1f || b.bottom !in 0f..1f || b.right <= b.left || b.bottom <= b.top }) {
            return EmbeddedEligibilityFailure.UnsupportedLayout
        }
        for (i in bounds.indices) for (j in i + 1 until bounds.size) {
            val a = bounds[i]
            val b = bounds[j]
            if (max(a.left, b.left) < min(a.right, b.right) &&
                max(a.top, b.top) < min(a.bottom, b.bottom)) {
                return EmbeddedEligibilityFailure.UnsupportedLayout
            }
        }
        return null
    }
}
