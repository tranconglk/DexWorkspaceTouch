package com.trancong.dexworkspacetouch.feature.car

import kotlinx.coroutines.delay

class CarActionEngine(
    private val executor: CarActionExecutor,
    private val delayAction: suspend (durationMillis: Long) -> Unit = { delay(it) },
) {
    suspend fun execute(workflow: CarWorkflow): CarWorkflowResult {
        workflow.actions.forEachIndexed { index, action ->
            val actionResult = when (action) {
                is CarAction.Delay -> {
                    delayAction(action.durationMillis)
                    CarActionResult.Success
                }
                else -> executor.execute(action)
            }

            if (actionResult is CarActionResult.Failure) {
                return CarWorkflowResult.Failure(
                    failedActionIndex = index,
                    failedAction = action,
                    error = actionResult.error,
                )
            }
        }
        return CarWorkflowResult.Success
    }
}
