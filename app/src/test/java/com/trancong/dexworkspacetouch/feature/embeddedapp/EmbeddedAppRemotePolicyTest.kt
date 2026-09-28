package com.trancong.dexworkspacetouch.feature.embeddedapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class EmbeddedAppRemotePolicyTest {
    private val target = EmbeddedAppTarget(
        "com.waze",
        "com.waze.FreeMapAppActivity",
        EmbeddedAppGeometry(900, 675, 320),
    )

    @Test fun rejectsComponentOutsidePackage() {
        assertThrows(IllegalArgumentException::class.java) {
            EmbeddedAppTarget("com.waze", "other.app.MainActivity", target.geometry)
        }
    }

    @Test fun acceptsProvenGeometryWithoutInventedUpperBound() {
        assertEquals(EmbeddedAppGeometry(900, 675, 320), target.geometry)
    }

    @Test fun excludesPreExistingTasksAndMatchesConfiguredTarget() {
        val candidates = listOf(
            EmbeddedAppTaskCandidate(10, 8, "com.waze", "com.waze.FreeMapAppActivity"),
            EmbeddedAppTaskCandidate(11, 8, "com.waze", "com.waze.FreeMapAppActivity"),
            EmbeddedAppTaskCandidate(12, 0, "com.waze", "com.waze.FreeMapAppActivity"),
        )
        assertEquals(11, selectNewTargetTask(candidates, setOf(10), target, 8)?.taskId)
    }

    @Test fun rejectsUnrelatedNewTaskFromSamePackage() {
        val candidates = listOf(
            EmbeddedAppTaskCandidate(11, 8, "com.waze", "com.waze.MainActivity"),
        )
        assertNull(selectNewTargetTask(candidates, emptySet(), target, 8))
    }

    @Test fun keepsRecordedTaskAfterInternalNavigationWithinTargetPackage() {
        val candidates = listOf(
            EmbeddedAppTaskCandidate(11, 8, "com.waze", "com.waze.MainActivity"),
        )
        assertEquals(11, selectRecordedTargetTask(candidates, 11, target, 8)?.taskId)
    }
    @Test fun returnsNullWhenNoNewTargetMatches() {
        assertNull(selectNewTargetTask(emptyList(), emptySet(), target, 8))
    }

    @Test fun rejectsAmbiguousNewTargetTasks() {
        val candidates = listOf(
            EmbeddedAppTaskCandidate(11, 8, "com.waze", "com.waze.FreeMapAppActivity"),
            EmbeddedAppTaskCandidate(12, 8, "com.waze", "com.waze.FreeMapAppActivity"),
        )
        assertThrows(IllegalStateException::class.java) {
            selectNewTargetTask(candidates, emptySet(), target, 8)
        }
    }
}
