package com.trancong.dexworkspacetouch.qualification

import org.junit.Assert.assertEquals
import org.junit.Test

class QualificationUiActionEvidenceTest {
    private fun node(target: Boolean? = true) = QualificationClickCandidate(target)

    @Test fun absenceIsRecordedWithoutInferringTimeoutCause() {
        assertEquals("NO_TEXT_MATCH", QualificationUiActionEvidence.classify(emptyList(), null))
    }
    @Test fun noClickableAncestorIsDifferentFromDisabledTarget() {
        assertEquals("NO_CLICK_TARGET", QualificationUiActionEvidence.classify(listOf(node(null)), null))
        assertEquals("NO_ENABLED_CLICK_TARGET", QualificationUiActionEvidence.classify(listOf(node(false)), null))
    }
    @Test fun enabledAncestorWithoutDispatchDoesNotClaimClickOrReadinessSuccess() {
        assertEquals("ACTION_NOT_DISPATCHED", QualificationUiActionEvidence.classify(listOf(node()), null))
    }
    @Test fun actionFalseIsPreservedAndDoesNotTriggerRetry() {
        assertEquals("ACTION_CLICK_FALSE", QualificationUiActionEvidence.classify(listOf(node()), false))
    }
    @Test fun actionTrueIsOnlyDispatchEvidence() {
        assertEquals("ACTION_CLICK_TRUE", QualificationUiActionEvidence.classify(listOf(node()), true))
    }
}
