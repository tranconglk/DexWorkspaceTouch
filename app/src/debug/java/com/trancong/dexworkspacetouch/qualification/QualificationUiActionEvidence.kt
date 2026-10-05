package com.trancong.dexworkspacetouch.qualification

/** Value-only diagnostic classification; never changes selector/action policy. */
data class QualificationClickCandidate(val clickableTargetEnabled: Boolean?)
object QualificationUiActionEvidence {
    fun classify(exactTextMatches: List<QualificationClickCandidate>, actionResult: Boolean?): String = when {
        actionResult == true -> "ACTION_CLICK_TRUE"
        actionResult == false -> "ACTION_CLICK_FALSE"
        exactTextMatches.isEmpty() -> "NO_TEXT_MATCH"
        exactTextMatches.all { it.clickableTargetEnabled == null } -> "NO_CLICK_TARGET"
        exactTextMatches.none { it.clickableTargetEnabled == true } -> "NO_ENABLED_CLICK_TARGET"
        else -> "ACTION_NOT_DISPATCHED"
    }
}
