package com.trancong.dexworkspacetouch.feature.car

sealed interface CarActionError {
    data class ExecutionFailed(val message: String? = null) : CarActionError
    data object AppUnavailable : CarActionError
    data object UriUnavailable : CarActionError
    data object WorkspaceUnavailable : CarActionError
    data class UnsupportedAction(val actionType: String) : CarActionError
}

sealed interface CarActionResult {
    data object Success : CarActionResult
    data class Failure(val error: CarActionError) : CarActionResult
}

fun interface CarActionExecutor {
    suspend fun execute(action: CarAction): CarActionResult
}

sealed interface CarWorkflowResult {
    data object Success : CarWorkflowResult

    data class Failure(
        val failedActionIndex: Int,
        val failedAction: CarAction,
        val error: CarActionError,
    ) : CarWorkflowResult
}
