package com.trancong.dexworkspacetouch.feature.car

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarWorkflowExecutionRunnerTest {
    @Test fun sharedArbiterPreventsParallelWorkflowsAcrossTwoSurfaceRunners() = runBlocking {
        val arbiter = CarWorkflowExecutionArbiter()
        val gate = CompletableDeferred<Unit>()
        val firstActions = mutableListOf<CarAction>()
        val secondActions = mutableListOf<CarAction>()
        val first = CarWorkflowExecutionRunner<String>(
            CarActionEngine(CarActionExecutor { action ->
                firstActions += action
                gate.await()
                CarActionResult.Success
            }),
            this,
            arbiter,
        )
        val second = CarWorkflowExecutionRunner<String>(
            CarActionEngine(CarActionExecutor { action ->
                secondActions += action
                CarActionResult.Success
            }),
            this,
            arbiter,
        )
        val firstWorkflow = CarWorkflow("activity", listOf(CarAction.Workspace("one")))
        val secondWorkflow = CarWorkflow("overlay", listOf(CarAction.Workspace("two")))

        assertTrue(first.acceptAndRun("car-screen", firstWorkflow))
        assertTrue(!second.acceptAndRun("floating-dock", secondWorkflow))
        withTimeout(2_000) { while (firstActions.isEmpty()) yield() }
        assertTrue(secondActions.isEmpty())
        gate.complete(Unit)
        withTimeout(2_000) { while (arbiter.isRunning.value) yield() }
        assertTrue(second.acceptAndRun("floating-dock", secondWorkflow))
        withTimeout(2_000) { while (secondActions.isEmpty()) yield() }
        Unit
    }

    @Test fun acceptedCallbackRunsOnceBeforeWorkflowAndRejectedRunDoesNotInvokeIt() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val engine = CarActionEngine(CarActionExecutor {
            events += "execute"
            gate.await()
            CarActionResult.Success
        })
        val runner = CarWorkflowExecutionRunner<String>(engine, this)
        val workflow = CarWorkflow("workflow", listOf(CarAction.Workspace("one")))

        assertTrue(runner.acceptAndRun("first", workflow) { events += "accepted" })
        assertTrue(!runner.acceptAndRun("second", workflow) { events += "rejected-callback" })
        withTimeout(2_000) { while (events.size < 2) yield() }
        assertEquals(listOf("accepted", "execute"), events)
        gate.complete(Unit)
        Unit
    }

    @Test fun sourceIsGeneric_runningRejectsParallelRun_andSuccessReturnsIdle() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val actions = mutableListOf<CarAction>()
        val engine = CarActionEngine(CarActionExecutor { action ->
            actions += action
            gate.await()
            CarActionResult.Success
        })
        val runner = CarWorkflowExecutionRunner<String>(engine, this)
        val first = CarWorkflow("first", listOf(CarAction.Workspace("one")))
        val second = CarWorkflow("second", listOf(CarAction.Workspace("two")))

        runner.acceptAndRun("slot-1", first)
        runner.acceptAndRun("slot-2", second)
        withTimeout(2_000) { while (actions.isEmpty()) yield() }
        assertEquals(listOf(CarAction.Workspace("one")), actions)
        assertEquals("slot-1", (runner.state.value as CarWorkflowExecutionState.Running).source)
        gate.complete(Unit)
        withTimeout(2_000) { runner.state.first { it is CarWorkflowExecutionState.Idle } }
        Unit
    }

    @Test fun disposeCancelsWorkflowWithoutTurningCancellationIntoError() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val engine = CarActionEngine(CarActionExecutor {
            entered.complete(Unit)
            CompletableDeferred<Unit>().await()
            CarActionResult.Success
        })
        val runner = CarWorkflowExecutionRunner<String>(engine, this)
        runner.acceptAndRun("slot", CarWorkflow("cancel", listOf(CarAction.Workspace("one"))))
        entered.await()

        runner.dispose()

        assertTrue(runner.state.value is CarWorkflowExecutionState.Idle)
        Unit
    }
}
