package com.trancong.dexworkspacetouch.feature.car

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CarActionEngineTest {
    @Test
    fun emptyWorkflow_succeedsWithoutCallingExecutor() = runBlocking {
        val executor = RecordingExecutor()

        val result = CarActionEngine(executor).execute(CarWorkflow("empty", emptyList()))

        assertSame(CarWorkflowResult.Success, result)
        assertTrue(executor.executedActions.isEmpty())
    }

    @Test
    fun oneAction_succeeds() = runBlocking {
        val action = CarAction.LaunchApp("com.example.maps")
        val executor = RecordingExecutor()

        val result = CarActionEngine(executor).execute(CarWorkflow("single", listOf(action)))

        assertSame(CarWorkflowResult.Success, result)
        assertEquals(listOf(action), executor.executedActions)
    }

    @Test
    fun multipleActions_executeSequentiallyInOrder() = runBlocking {
        val actions = listOf(
            CarAction.LaunchApp("com.example.maps"),
            CarAction.OpenUri("maps://home"),
            CarAction.Workspace("drive"),
        )
        val executor = RecordingExecutor()

        val result = CarActionEngine(executor).execute(CarWorkflow("ordered", actions))

        assertSame(CarWorkflowResult.Success, result)
        assertEquals(actions, executor.executedActions)
    }

    @Test
    fun firstFailure_stopsWorkflowAndReportsActionAndIndex() = runBlocking {
        val first = CarAction.LaunchApp("com.example.maps")
        val failed = CarAction.OpenUri("maps://missing")
        val skipped = CarAction.Workspace("drive")
        val error = CarActionError.UriUnavailable
        val executor = RecordingExecutor(failureAction = failed, failureError = error)

        val result = CarActionEngine(executor).execute(
            CarWorkflow("failure", listOf(first, failed, skipped)),
        )

        assertEquals(listOf(first, failed), executor.executedActions)
        assertEquals(
            CarWorkflowResult.Failure(
                failedActionIndex = 1,
                failedAction = failed,
                error = error,
            ),
            result,
        )
    }

    @Test
    fun delayZero_succeedsWithoutCallingExecutor() = runBlocking {
        val executor = RecordingExecutor()
        val observedDelays = mutableListOf<Long>()
        val engine = CarActionEngine(executor) { observedDelays += it }

        val result = engine.execute(CarWorkflow("delay-zero", listOf(CarAction.Delay(0L))))

        assertSame(CarWorkflowResult.Success, result)
        assertEquals(listOf(0L), observedDelays)
        assertTrue(executor.executedActions.isEmpty())
    }

    @Test
    fun defaultDelay_suspendsInsteadOfBlockingCallingThread() = runBlocking {
        val engine = CarActionEngine(RecordingExecutor())

        val result = async(start = CoroutineStart.UNDISPATCHED) {
            engine.execute(CarWorkflow("delay", listOf(CarAction.Delay(10_000L))))
        }

        assertFalse(result.isCompleted)
        result.cancelAndJoin()
    }

    @Test
    fun cancellation_propagatesAndStopsWorkflow() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val executedActions = mutableListOf<CarAction>()
        val first = CarAction.LaunchApp("com.example.maps")
        val skipped = CarAction.Workspace("drive")
        val engine = CarActionEngine(
            executor = CarActionExecutor { action ->
                executedActions += action
                started.complete(Unit)
                awaitCancellation()
            },
        )

        val workflowJob = launch {
            engine.execute(CarWorkflow("cancelled", listOf(first, skipped)))
        }
        started.await()
        workflowJob.cancelAndJoin()

        assertTrue(workflowJob.isCancelled)
        assertEquals(listOf(first), executedActions)
    }

    @Test
    fun workflowCopiesItsActionList() = runBlocking {
        val action = CarAction.Workspace("drive")
        val sourceActions = mutableListOf<CarAction>(action)
        val workflow = CarWorkflow("immutable", sourceActions)
        sourceActions.clear()
        val executor = RecordingExecutor()

        val result = CarActionEngine(executor).execute(workflow)

        assertSame(CarWorkflowResult.Success, result)
        assertEquals(listOf(action), workflow.actions)
        assertEquals(listOf(action), executor.executedActions)
    }

    private class RecordingExecutor(
        private val failureAction: CarAction? = null,
        private val failureError: CarActionError = CarActionError.ExecutionFailed(),
    ) : CarActionExecutor {
        val executedActions = mutableListOf<CarAction>()

        override suspend fun execute(action: CarAction): CarActionResult {
            executedActions += action
            return if (action == failureAction) {
                CarActionResult.Failure(failureError)
            } else {
                CarActionResult.Success
            }
        }
    }
}
