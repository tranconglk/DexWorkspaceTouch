package com.trancong.dexworkspacetouch.feature.embeddedworkspace

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedGeometryPolicy
import java.util.Collections

sealed interface GeometrySnapshotResult {
    data class Ready(val snapshot: EmbeddedWorkspaceGeometrySnapshot) : GeometrySnapshotResult
    data class GeometryRejected(val sourceCellId: String, val message: String?) : GeometrySnapshotResult
}

class EmbeddedWorkspaceGeometrySnapshot private constructor(
    val planSnapshot: EmbeddedWorkspacePlan,
    private val resolved: Map<String, EmbeddedAppGeometry>,
) {
    val geometryBySourceId: Map<String, EmbeddedAppGeometry> get() = resolved

    fun geometryFor(sourceCellId: String): EmbeddedAppGeometry = resolved.getValue(sourceCellId)

    fun asPolicy(): EmbeddedGeometryPolicy = EmbeddedGeometryPolicy { item ->
        require(planSnapshot.items.any { it == item }) { "Item does not belong to this renderer generation" }
        geometryFor(item.sourceCellId)
    }

    companion object {
        fun resolve(plan: EmbeddedWorkspacePlan, policy: EmbeddedGeometryPolicy): GeometrySnapshotResult {
            val frozenPlan = plan.copy(items = plan.items.toList())
            val geometry = linkedMapOf<String, EmbeddedAppGeometry>()
            for (item in frozenPlan.items.sortedBy { it.order }) {
                geometry[item.sourceCellId] = try {
                    policy.geometryFor(item)
                } catch (failure: Exception) {
                    return GeometrySnapshotResult.GeometryRejected(item.sourceCellId, failure.message)
                }
            }
            return GeometrySnapshotResult.Ready(
                EmbeddedWorkspaceGeometrySnapshot(frozenPlan, Collections.unmodifiableMap(geometry)),
            )
        }
    }
}
