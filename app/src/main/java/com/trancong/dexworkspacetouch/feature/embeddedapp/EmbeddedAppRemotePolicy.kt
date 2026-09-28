package com.trancong.dexworkspacetouch.feature.embeddedapp

data class EmbeddedAppTaskCandidate(
    val taskId: Int,
    val displayId: Int,
    val packageName: String?,
    val componentName: String?,
)

fun selectNewTargetTask(
    candidates: List<EmbeddedAppTaskCandidate>,
    beforeTaskIds: Set<Int>,
    target: EmbeddedAppTarget,
    expectedDisplayId: Int,
): EmbeddedAppTaskCandidate? {
    val matches = candidates.filter {
        it.taskId !in beforeTaskIds &&
            it.displayId == expectedDisplayId &&
            it.packageName == target.packageName &&
            it.componentName == target.componentName
    }
    check(matches.size <= 1) { "Ambiguous embedded target tasks ${matches.map { it.taskId }}" }
    return matches.singleOrNull()
}

fun selectRecordedTargetTask(
    candidates: List<EmbeddedAppTaskCandidate>,
    recordedTaskId: Int,
    target: EmbeddedAppTarget,
    expectedDisplayId: Int,
): EmbeddedAppTaskCandidate? = candidates.singleOrNull {
    it.taskId == recordedTaskId &&
        it.displayId == expectedDisplayId &&
        it.packageName == target.packageName
}
