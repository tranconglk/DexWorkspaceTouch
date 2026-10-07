package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionArbiter
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.EmbeddedProductRunGate
import com.trancong.dexworkspacetouch.platform.launch.bounds.*
import com.trancong.dexworkspacetouch.workspace.launcher.model.*

enum class RepairCellStatus { CORRECT, REPAIRED, MISSING, UNRESOLVED, RESIZE_FAILED, READBACK_FAILED }
enum class WorkspaceAssessmentStatus { LAYOUT_CORRECT, REPAIR_AVAILABLE, PARTIAL_OR_UNRESOLVED, UNAVAILABLE }
enum class AssessmentCellStatus { CORRECT, WRONG_BOUNDS, MISSING, UNRESOLVED }
data class AssessmentCellEvidence(val cellId: String, val component: String?, val expected: PixelBounds?,
    val observed: WorkspaceTask?, val taskIdentity: String?, val status: AssessmentCellStatus)
data class ExistingWorkspaceAssessmentReport(val status: WorkspaceAssessmentStatus,
    val cells: List<AssessmentCellEvidence> = emptyList(), val detail: String = "")
data class RepairCellEvidence(val cellId: String, val component: String?, val expected: PixelBounds?,
    val before: WorkspaceTask?, val after: WorkspaceTask?, val status: RepairCellStatus, val detail: String = "",
    val taskIdentity: String? = null)
data class ExistingWorkspaceRepairReport(val admitted: Boolean, val cells: List<RepairCellEvidence>) {
    val complete: Boolean get() = admitted && cells.isNotEmpty() && cells.all {
        it.status == RepairCellStatus.CORRECT || it.status == RepairCellStatus.REPAIRED
    }
}

/** Existing-task observation, including Task and ActivityRecord object identities. No package fallback. */
internal data class RepairTaskObservation(val task: WorkspaceTask, val identity: String?, val activities: Set<String>)
internal object ExistingTaskCorrelation {
    fun parse(dump: String): List<RepairTaskObservation> {
        val identities = mutableMapOf<Int, Pair<String, MutableList<String>>>()
        val components = mutableMapOf<Int, MutableSet<String>>()
        var current: Int? = null
        val taskPattern = Regex("^\\s*\\* Task\\{([^ ]+) #(\\d+)\\b.*")
        val activityPattern = Regex("^\\s*\\* Hist\\s+#\\d+: ActivityRecord\\{([^ ]+) u(\\d+) ([^ ]+) t(\\d+)[ }].*")
        dump.lineSequence().takeWhile { !it.startsWith("ActivityTaskSupervisor state:") }.forEach { line ->
            taskPattern.matchEntire(line)?.let {
                current = it.groupValues[2].toInt()
                identities[current!!] = it.groupValues[1] to mutableListOf()
                components[current!!] = mutableSetOf()
            }
            activityPattern.matchEntire(line)?.let {
                val id = current
                if (id != null && it.groupValues[4].toInt() == id) {
                    val component = runCatching { WorkspaceTaskCorrelation.canonicalComponent(it.groupValues[3]) }.getOrNull()
                    if (component != null) {
                        identities[id]?.second?.add("${it.groupValues[1]}:${it.groupValues[2]}:$component")
                        components[id]?.add(component)
                    }
                }
            }
        }
        return WorkspaceTaskCorrelation.parse(dump).map { task ->
            val records = identities[task.id]
            val identity = records?.takeIf { pair -> pair.second.isNotEmpty() && pair.second.any {
                it.substringAfter(':') == "${task.userId}:${task.component}"
            } }?.let { it.first + ":" + it.second.sorted().joinToString("|") }
            RepairTaskObservation(task, identity, components[task.id].orEmpty())
        }
    }
    fun select(tasks: List<RepairTaskObservation>, component: String, displayId: Int): RepairTaskObservation? {
        val candidates = tasks.filter { it.task.component == component && it.task.displayId == displayId &&
            it.task.userId == 0 && it.task.visible && it.task.freeform }
        return candidates.singleOrNull()?.takeIf { candidate -> candidate.identity != null &&
            candidate.task.bounds != null && tasks.count { it.task.id == candidate.task.id } == 1 }
    }
    fun same(a: RepairTaskObservation, b: RepairTaskObservation) = a.identity != null && a.identity == b.identity &&
        WorkspaceTaskCorrelation.sameIdentity(a.task,b.task)
}

/** Repairs existing tasks only, under the shared Classic admission boundary. */
class ExistingWorkspaceRepair(private val gate: EmbeddedProductRunGate,
    private val arbiter: CarWorkflowExecutionArbiter, private val shell: WorkspaceCommandShell,
    private val pause: () -> Unit = { Thread.sleep(100) }, private val pollAttempts: Int = 20) {
    init { require(pollAttempts >= 3) }
    /** SWC-002: one read under the same admission rules; this path never calls repair or mutates tasks. */
    fun assess(request: WorkspaceLaunchRequest, snapshot: DisplayWorkAreaSnapshot,
        onReservation: (Boolean) -> Unit = {}): ExistingWorkspaceAssessmentReport {
        fun unavailable(detail: String) = ExistingWorkspaceAssessmentReport(WorkspaceAssessmentStatus.UNAVAILABLE, detail=detail)
        if (snapshot.displayId <= 0) return unavailable("External display unavailable")
        onReservation(true)
        if (!arbiter.tryAcquire()) { onReservation(false); return unavailable("ADMISSION_BLOCKED") }
        try {
            var report = unavailable("ADMISSION_BLOCKED")
            val admitted = gate.tryDispatchClassic {
                try {
                    val tasks = readTasks()
                    val components = request.targets.map(::component)
                    val cells = request.targets.sortedBy(AppLaunchTarget::order).map { target ->
                        val component = component(target)
                        val expected = (LaunchBoundsCalculator(launchMarginPx(snapshot.density)).calculate(target.bounds,
                            snapshot.workArea) as? BoundsCalculationResult.Success)?.bounds
                        val selected = component?.let { ExistingTaskCorrelation.select(tasks,it,snapshot.displayId) }
                        val status = when {
                            component == null || expected == null || components.count { it == component } > 1 -> AssessmentCellStatus.UNRESOLVED
                            selected == null -> if (tasks.any { it.task.displayId == snapshot.displayId && it.task.userId == 0 &&
                                (component in it.activities || it.task.component?.substringBefore('/') == target.identity.packageName) })
                                AssessmentCellStatus.UNRESOLVED else AssessmentCellStatus.MISSING
                            WorkspaceTaskCorrelation.withinTolerance(expected,selected.task.bounds) -> AssessmentCellStatus.CORRECT
                            else -> AssessmentCellStatus.WRONG_BOUNDS
                        }
                        AssessmentCellEvidence(target.sourceCellId,component,expected,selected?.task,selected?.identity,status)
                    }
                    report = ExistingWorkspaceAssessmentReport(when {
                        cells.any { it.status == AssessmentCellStatus.WRONG_BOUNDS } -> WorkspaceAssessmentStatus.REPAIR_AVAILABLE
                        cells.all { it.status == AssessmentCellStatus.CORRECT } -> WorkspaceAssessmentStatus.LAYOUT_CORRECT
                        else -> WorkspaceAssessmentStatus.PARTIAL_OR_UNRESOLVED
                    },cells)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw interrupted
                } catch (error: Exception) { report = unavailable(error.message ?: "Task inspection unavailable") }
            }
            return if (admitted) report else unavailable("ADMISSION_BLOCKED")
        } finally { onReservation(false); arbiter.release() }
    }
    fun run(request: WorkspaceLaunchRequest, snapshot: DisplayWorkAreaSnapshot,
        expectedAssessment: ExistingWorkspaceAssessmentReport? = null): ExistingWorkspaceRepairReport {
        fun blocked() = ExistingWorkspaceRepairReport(false,request.targets.map {
            RepairCellEvidence(it.sourceCellId,null,null,null,null,RepairCellStatus.UNRESOLVED,"ADMISSION_BLOCKED")
        })
        if (!arbiter.tryAcquire()) return blocked()
        try {
            val cells = mutableListOf<RepairCellEvidence>()
            val admitted = gate.tryDispatchClassic {
                val initial = runCatching { readTasks() }
                // Automatic evidence is only an identity precondition. Bounds and tasks are always read anew.
                if (expectedAssessment != null && (expectedAssessment.status != WorkspaceAssessmentStatus.REPAIR_AVAILABLE ||
                    expectedAssessment.cells.filter { it.observed != null }.any { cell ->
                        val current = cell.component?.let { component -> initial.getOrNull()?.let {
                            ExistingTaskCorrelation.select(it, component, snapshot.displayId)
                        } }
                        current == null || current.identity != cell.taskIdentity ||
                            !WorkspaceTaskCorrelation.sameIdentity(cell.observed!!, current.task)
                    })) {
                    cells += request.targets.map { RepairCellEvidence(it.sourceCellId,component(it),null,null,null,
                        RepairCellStatus.UNRESOLVED,"Assessment identity changed before Repair") }
                    return@tryDispatchClassic
                }
                val components = request.targets.map { component(it) }
                request.targets.sortedBy(AppLaunchTarget::order).forEach { target ->
                    val component = component(target)
                    val duplicate = component != null && components.count { it == component } > 1
                    cells += repair(target,snapshot,initial,duplicate)
                }
                if (cells.any { it.status == RepairCellStatus.REPAIRED || it.status == RepairCellStatus.CORRECT }) {
                    val final = runCatching { readTasks() }
                    cells.indices.forEach { index ->
                        val cell = cells[index]
                        if (cell.status != RepairCellStatus.REPAIRED && cell.status != RepairCellStatus.CORRECT) return@forEach
                        val observed = final.getOrNull()?.let { ExistingTaskCorrelation.select(it,cell.component!!,snapshot.displayId) }
                        if (observed == null || cell.after == null || observed.identity != cell.taskIdentity ||
                            !WorkspaceTaskCorrelation.sameIdentity(cell.after,observed.task) ||
                            !WorkspaceTaskCorrelation.withinTolerance(cell.expected!!,observed.task.bounds))
                            cells[index] = cell.copy(after=observed?.task,status=RepairCellStatus.READBACK_FAILED,
                                detail="Final workspace identity/uniqueness/bounds readback failed")
                        else cells[index] = cell.copy(after=observed.task)
                    }
                }
            }
            return if (admitted) ExistingWorkspaceRepairReport(true,cells) else blocked()
        } finally { arbiter.release() }
    }
    private fun readTasks(): List<RepairTaskObservation> {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("Repair interrupted")
        val result = shell.execute(WorkspaceTaskCorrelation.DUMP_COMMAND)
        check(result.exitCode == 0) { "Task inspection failed" }
        return ExistingTaskCorrelation.parse(result.output)
    }
    private fun component(target: AppLaunchTarget): String? = target.identity.activityName?.let {
        runCatching { WorkspaceTaskCorrelation.canonicalComponent("${target.identity.packageName}/$it") }.getOrNull()
    }
    private fun repair(target: AppLaunchTarget, snapshot: DisplayWorkAreaSnapshot,
        initial: Result<List<RepairTaskObservation>>, duplicate: Boolean): RepairCellEvidence {
        val component = component(target)
        val expected = (LaunchBoundsCalculator(launchMarginPx(snapshot.density)).calculate(target.bounds,
            snapshot.workArea) as? BoundsCalculationResult.Success)?.bounds
        var selected: RepairTaskObservation? = null
        var observed: RepairTaskObservation? = null
        var failureStatus = RepairCellStatus.UNRESOLVED
        fun result(status: RepairCellStatus, detail: String = "") = RepairCellEvidence(target.sourceCellId,
            component,expected,selected?.task,observed?.task,status,detail,selected?.identity)
        try {
            if (component == null || duplicate || snapshot.displayId <= 0 || expected == null)
                return result(RepairCellStatus.UNRESOLVED,"Exact component, unique workspace cell and external geometry required")
            val tasks = initial.getOrThrow()
            selected = ExistingTaskCorrelation.select(tasks,component,snapshot.displayId)
            if (selected == null) {
                val related = tasks.any { it.task.displayId == snapshot.displayId && it.task.userId == 0 &&
                    (component in it.activities || it.task.component?.substringBefore('/') == target.identity.packageName) }
                return result(if (related) RepairCellStatus.UNRESOLVED else RepairCellStatus.MISSING,
                    "No unique visible freeform task with exact component/activity identity; missing apps are not launched")
            }
            observed = selected
            if (WorkspaceTaskCorrelation.withinTolerance(expected,selected!!.task.bounds))
                return result(RepairCellStatus.CORRECT,"NO_CHANGE")
            // Re-run exact correlation, including uniqueness and ActivityRecord identity, immediately before mutation.
            observed = ExistingTaskCorrelation.select(readTasks(),component,snapshot.displayId)
            if (observed == null || !ExistingTaskCorrelation.same(selected!!,observed!!))
                return result(RepairCellStatus.UNRESOLVED,"Identity or uniqueness changed before resize")
            if (WorkspaceTaskCorrelation.withinTolerance(expected,observed!!.task.bounds))
                return result(RepairCellStatus.CORRECT,"NO_CHANGE")
            failureStatus = RepairCellStatus.RESIZE_FAILED
            val resized = shell.execute(listOf("am","task","resize",selected!!.task.id.toString(),
                expected.left.toString(),expected.top.toString(),expected.right.toString(),expected.bottom.toString()))
            if (resized.exitCode != 0 || resized.output.contains("Exception") || resized.output.contains("Error:"))
                return result(RepairCellStatus.RESIZE_FAILED,resized.output.take(1000))
            failureStatus = RepairCellStatus.READBACK_FAILED
            var stable = 0
            repeat(pollAttempts) { attempt ->
                observed = ExistingTaskCorrelation.select(readTasks(),component,snapshot.displayId)
                if (observed == null || !ExistingTaskCorrelation.same(selected!!,observed!!))
                    return result(RepairCellStatus.READBACK_FAILED,"Identity or uniqueness changed after resize")
                stable = if (WorkspaceTaskCorrelation.withinTolerance(expected,observed!!.task.bounds)) stable + 1 else 0
                if (stable >= 3) return result(RepairCellStatus.REPAIRED)
                if (attempt < pollAttempts-1) pause()
            }
            return result(RepairCellStatus.READBACK_FAILED,"Bounds failed three stable observations within 2px")
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (error: Exception) { return result(failureStatus,"${error.javaClass.simpleName}: ${error.message}") }
    }
}
