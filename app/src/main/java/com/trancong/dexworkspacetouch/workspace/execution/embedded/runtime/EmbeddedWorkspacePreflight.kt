package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget

class EmbeddedWorkspacePreflight(
    private val geometryPolicy: EmbeddedGeometryPolicy,
) {
    fun prepare(request: EmbeddedWorkspaceExecutionRequest): EmbeddedWorkspacePreflightResult {
        val planItems = request.plan.items
        val slots = request.hostSlots

        val blankSlot = slots.firstOrNull { it.sourceCellId.isBlank() }
        if (blankSlot != null) return rejected(
            EmbeddedWorkspacePreflightRejection.InvalidPlan("Host slot sourceCellId must not be blank"),
        )

        slots.groupBy { it.sourceCellId }.entries.firstOrNull { it.value.size > 1 }?.let {
            return rejected(EmbeddedWorkspacePreflightRejection.DuplicateSlot(it.key))
        }

        val planIds = planItems.map { it.sourceCellId }.toSet()
        val slotIds = slots.map { it.sourceCellId }.toSet()
        (planIds - slotIds).sorted().firstOrNull()?.let {
            return rejected(EmbeddedWorkspacePreflightRejection.MissingSlot(it))
        }
        (slotIds - planIds).sorted().firstOrNull()?.let {
            return rejected(EmbeddedWorkspacePreflightRejection.UnknownSlot(it))
        }

        val slotsById = slots.associateBy { it.sourceCellId }
        planItems.sortedBy { it.order }.firstOrNull { slotsById.getValue(it.sourceCellId).executionSurface.isValid.not() }?.let {
            return rejected(EmbeddedWorkspacePreflightRejection.InvalidSurface(it.sourceCellId))
        }

        planItems
            .groupBy { it.packageName to it.componentName }
            .entries
            .firstOrNull { it.value.size > 1 }
            ?.let { duplicate ->
                return rejected(
                    EmbeddedWorkspacePreflightRejection.DuplicateTargetIdentity(
                        packageName = duplicate.key.first,
                        componentName = duplicate.key.second,
                        sourceCellIds = duplicate.value.sortedBy { it.order }.map { it.sourceCellId },
                    ),
                )
            }

        val prepared = ArrayList<EmbeddedWorkspacePreparedItem>(planItems.size)
        for (item in planItems.sortedBy { it.order }) {
            val geometry = try {
                geometryPolicy.geometryFor(item)
            } catch (failure: Exception) {
                return rejected(
                    EmbeddedWorkspacePreflightRejection.GeometryRejected(item.sourceCellId, failure.message),
                )
            }

            val target = try {
                EmbeddedAppTarget(item.packageName, item.componentName, geometry)
            } catch (failure: IllegalArgumentException) {
                return rejected(
                    EmbeddedWorkspacePreflightRejection.GeometryRejected(item.sourceCellId, failure.message),
                )
            }

            prepared += EmbeddedWorkspacePreparedItem(
                planItem = item,
                target = target,
                executionSurface = slotsById.getValue(item.sourceCellId).executionSurface,
            )
        }
        return EmbeddedWorkspacePreflightResult.Prepared(prepared)
    }

    private fun rejected(rejection: EmbeddedWorkspacePreflightRejection) =
        EmbeddedWorkspacePreflightResult.Rejected(rejection)
}
