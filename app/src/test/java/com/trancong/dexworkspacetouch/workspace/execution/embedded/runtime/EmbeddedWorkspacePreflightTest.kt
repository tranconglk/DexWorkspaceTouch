package com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime

import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppGeometry
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlan
import com.trancong.dexworkspacetouch.workspace.execution.embedded.EmbeddedWorkspacePlanItem
import java.lang.reflect.Modifier
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedWorkspacePreflightTest {
    @Test
    fun `exact plan slot set passes and prepared items are sorted by plan order`() {
        val plan = plan(
            item("second", "com.second", "com.second.Main", order = 1),
            item("first", "com.first", "com.first.Main", order = 0),
        )
        val request = request(
            plan,
            slot("second"),
            slot("first"),
        )

        val result = EmbeddedWorkspacePreflight {
            if (it.sourceCellId == "first") EmbeddedAppGeometry(800, 600, 280)
            else EmbeddedAppGeometry(900, 675, 320)
        }.prepare(request)

        val prepared = result as EmbeddedWorkspacePreflightResult.Prepared
        assertEquals(listOf("first", "second"), prepared.items.map { it.planItem.sourceCellId })
        assertEquals(listOf(0, 1), prepared.items.map { it.planItem.order })
        assertEquals(listOf(800, 900), prepared.items.map { it.target.geometry.width })
    }

    @Test
    fun `missing slot rejects before geometry or session allocation`() {
        var geometryCalls = 0
        val (result, allocations) = prepareAndAllocate(
            request(
                plan(
                    item("first", "com.first", "com.first.Main", 0),
                    item("second", "com.second", "com.second.Main", 1),
                ),
                slot("first"),
            ),
        ) {
            geometryCalls++
            EmbeddedAppGeometry(900, 675, 320)
        }

        assertEquals(EmbeddedWorkspacePreflightRejection.MissingSlot("second"), rejection(result))
        assertEquals(0, geometryCalls)
        assertEquals(0, allocations)
    }

    @Test
    fun `extra unknown slot rejects before geometry or session allocation`() {
        var geometryCalls = 0
        val (result, allocations) = prepareAndAllocate(
            request(
                plan(item("first", "com.first", "com.first.Main", 0)),
                slot("first"),
                slot("unknown"),
            ),
        ) {
            geometryCalls++
            EmbeddedAppGeometry(900, 675, 320)
        }

        assertEquals(EmbeddedWorkspacePreflightRejection.UnknownSlot("unknown"), rejection(result))
        assertEquals(0, geometryCalls)
        assertEquals(0, allocations)
    }

    @Test
    fun `duplicate host slot rejects before geometry or session allocation`() {
        var geometryCalls = 0
        val (result, allocations) = prepareAndAllocate(
            request(
                plan(item("first", "com.first", "com.first.Main", 0)),
                slot("first"),
                slot("first"),
            ),
        ) {
            geometryCalls++
            EmbeddedAppGeometry(900, 675, 320)
        }

        assertEquals(EmbeddedWorkspacePreflightRejection.DuplicateSlot("first"), rejection(result))
        assertEquals(0, geometryCalls)
        assertEquals(0, allocations)
    }

    @Test
    fun `blank host slot source id rejects before geometry or session allocation`() {
        var geometryCalls = 0
        val (result, allocations) = prepareAndAllocate(
            request(
                plan(item("first", "com.first", "com.first.Main", 0)),
                EmbeddedWorkspaceHostSlot(" ", FakeSurface(isValid = true)),
            ),
        ) {
            geometryCalls++
            EmbeddedAppGeometry(900, 675, 320)
        }

        assertTrue(rejection(result) is EmbeddedWorkspacePreflightRejection.InvalidPlan)
        assertEquals(0, geometryCalls)
        assertEquals(0, allocations)
    }

    @Test
    fun `invalid execution surface rejects before geometry or session allocation`() {
        var geometryCalls = 0
        val (result, allocations) = prepareAndAllocate(
            request(
                plan(item("first", "com.first", "com.first.Main", 0)),
                slot("first", valid = false),
            ),
        ) {
            geometryCalls++
            EmbeddedAppGeometry(900, 675, 320)
        }

        assertEquals(EmbeddedWorkspacePreflightRejection.InvalidSurface("first"), rejection(result))
        assertEquals(0, geometryCalls)
        assertEquals(0, allocations)
    }

    @Test
    fun `duplicate package component identity rejects at execution layer without changing pure plan`() {
        val duplicatePlan = plan(
            item("left", "com.app", "com.app.Main", 0),
            item("right", "com.app", "com.app.Main", 1),
        )
        var geometryCalls = 0

        val (result, allocations) = prepareAndAllocate(
            request(duplicatePlan, slot("left"), slot("right")),
        ) {
            geometryCalls++
            EmbeddedAppGeometry(900, 675, 320)
        }

        assertEquals(2, duplicatePlan.items.size)
        assertEquals(
            EmbeddedWorkspacePreflightRejection.DuplicateTargetIdentity(
                packageName = "com.app",
                componentName = "com.app.Main",
                sourceCellIds = listOf("left", "right"),
            ),
            rejection(result),
        )
        assertEquals(0, geometryCalls)
        assertEquals(0, allocations)
    }

    @Test
    fun `geometry exception rejects complete request before session allocation`() {
        val (result, allocations) = prepareAndAllocate(
            request(
                plan(
                    item("first", "com.first", "com.first.Main", 0),
                    item("second", "com.second", "com.second.Main", 1),
                ),
                slot("first"),
                slot("second"),
            ),
        ) { planItem ->
            if (planItem.sourceCellId == "second") error("unsupported geometry")
            EmbeddedAppGeometry(900, 675, 320)
        }

        val rejected = rejection(result) as EmbeddedWorkspacePreflightRejection.GeometryRejected
        assertEquals("second", rejected.sourceCellId)
        assertTrue(rejected.message.orEmpty().contains("unsupported geometry"))
        assertEquals(0, allocations)
    }

    @Test
    fun `constructed target contains only package component and policy geometry`() {
        val geometry = EmbeddedAppGeometry(1111, 777, 333)
        val result = EmbeddedWorkspacePreflight { geometry }.prepare(
            request(
                plan(item("cell", "com.app", "com.app.Main", 0)),
                slot("cell"),
            ),
        ) as EmbeddedWorkspacePreflightResult.Prepared

        val prepared = result.items.single()
        assertEquals(EmbeddedAppTarget("com.app", "com.app.Main", geometry), prepared.target)
        assertEquals(
            setOf("packageName", "componentName", "geometry"),
            EmbeddedAppTarget::class.java.declaredFields
                .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
                .map { it.name }
                .toSet(),
        )
    }

    @Test
    fun `normalized bounds remain metadata and do not influence policy geometry`() {
        val leftBounds = NormalizedBounds(0f, 0f, 0.25f, 1f)
        val rightBounds = NormalizedBounds(0.25f, 0f, 1f, 1f)
        val fixedGeometry = EmbeddedAppGeometry(900, 675, 320)
        val preflight = EmbeddedWorkspacePreflight { fixedGeometry }

        val left = preflight.prepare(
            request(
                plan(item("cell", "com.app", "com.app.Main", 0, leftBounds)),
                slot("cell"),
            ),
        ) as EmbeddedWorkspacePreflightResult.Prepared
        val right = preflight.prepare(
            request(
                plan(item("cell", "com.app", "com.app.Main", 0, rightBounds)),
                slot("cell"),
            ),
        ) as EmbeddedWorkspacePreflightResult.Prepared

        assertEquals(leftBounds, left.items.single().planItem.normalizedBounds)
        assertEquals(rightBounds, right.items.single().planItem.normalizedBounds)
        assertEquals(fixedGeometry, left.items.single().target.geometry)
        assertEquals(fixedGeometry, right.items.single().target.geometry)
    }

    @Test
    fun `proof timeout policy uses approved ready active and cleanup durations`() {
        assertEquals(10.seconds, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS.readyTimeout)
        assertEquals(30.seconds, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS.activeTimeout)
        assertEquals(20.seconds, PROOF_EMBEDDED_WORKSPACE_TIMEOUTS.cleanupTimeout)
    }

    private fun prepareAndAllocate(
        request: EmbeddedWorkspaceExecutionRequest,
        geometryPolicy: EmbeddedGeometryPolicy,
    ): Pair<EmbeddedWorkspacePreflightResult, Int> {
        val result = EmbeddedWorkspacePreflight(geometryPolicy).prepare(request)
        val factory = CountingSessionFactory()
        if (result is EmbeddedWorkspacePreflightResult.Prepared) {
            result.items.forEach { factory.create(it.target) }
        }
        return result to factory.invocationCount
    }

    private fun rejection(
        result: EmbeddedWorkspacePreflightResult,
    ): EmbeddedWorkspacePreflightRejection =
        (result as EmbeddedWorkspacePreflightResult.Rejected).rejection

    private fun request(
        plan: EmbeddedWorkspacePlan,
        vararg slots: EmbeddedWorkspaceHostSlot,
    ) = EmbeddedWorkspaceExecutionRequest(plan, slots.toList())

    private fun plan(vararg items: EmbeddedWorkspacePlanItem) = EmbeddedWorkspacePlan(
        workspaceId = "workspace",
        workspaceName = "Workspace",
        items = items.toList(),
    )

    private fun item(
        sourceCellId: String,
        packageName: String,
        componentName: String,
        order: Int,
        bounds: NormalizedBounds = NormalizedBounds.FullCanvas,
    ) = EmbeddedWorkspacePlanItem(
        sourceCellId = sourceCellId,
        packageName = packageName,
        componentName = componentName,
        normalizedBounds = bounds,
        order = order,
    )

    private fun slot(sourceCellId: String, valid: Boolean = true) =
        EmbeddedWorkspaceHostSlot(sourceCellId, FakeSurface(valid))

    private data class FakeSurface(override val isValid: Boolean) : EmbeddedWorkspaceExecutionSurface

    private class CountingSessionFactory {
        var invocationCount: Int = 0
            private set

        fun create(target: EmbeddedAppTarget) {
            check(target.packageName.isNotBlank())
            invocationCount++
        }
    }
}
