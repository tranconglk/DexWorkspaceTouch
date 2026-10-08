package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import kotlin.math.abs

data class WorkspaceCommandResult(val exitCode: Int, val output: String)
fun interface WorkspaceCommandShell { fun execute(arguments: List<String>): WorkspaceCommandResult }
data class WorkspaceTask(val id: Int, val displayId: Int, val userId: Int, val component: String?,
    val bounds: PixelBounds?, val freeform: Boolean, val visible: Boolean, val launchIdentifier: String? = null)

internal data class WorkspaceActivityRecord(val token: String, val userId: Int, val component: String,
    val taskId: Int, val visible: Boolean?)
internal data class WorkspaceTaskRecord(val task: WorkspaceTask, val token: String?,
    val activities: List<WorkspaceActivityRecord>, val valid: Boolean)

/** Conservative task observation; no task launch or process transport. */
object WorkspaceTaskCorrelation {
    private val displayPattern = Regex("^Display #(\\d+)(?:\\s.*)?$")
    private val taskPattern = Regex("^\\* Task(?:Record)?\\{([^ ]+) #(\\d+)\\b.*\\}$")
    private val taskBoundaryPattern = Regex("^(?:\\*\\s+)?Task(?:Record)?\\{")
    private val stackPattern = Regex("^Stack #(\\d+):.*")
    private val modePattern = Regex("\\bmode=(\\w+)\\b")
    private val stackIdPattern = Regex("\\bStackId=(\\d+)\\b")
    private val legacyTaskIdPattern = Regex("^Task id #(\\d+)$")
    private val activityPattern = Regex("^\\* Hist\\s+#\\d+: ActivityRecord\\{([^ ]+) u(\\d+) ([^ ]+) t(\\d+)(?: [^}]*)?\\}.*")
    private val visibilityPattern = Regex("\\bvisible=(true|false)\\b")
    private val userPattern = Regex("\\bU=(\\d+)\\b")
    private val boundsPattern = Regex("^mBounds=Rect\\((\\d+), (\\d+) - (\\d+), (\\d+)\\)$")
    private val componentPattern = Regex("\\bcmp=([^\\s}]+)")
    private val identifierPattern = Regex("\\bid=([^\\s}]+)")

    private data class StackContext(val id: Int, val mode: String?)
    private class BoundsContext {
        var value: PixelBounds? = null
        var invalid = false
    }
    private data class PreambleContext(val id: Int, val indent: Int, val bounds: BoundsContext = BoundsContext())
    private data class ActivityContext(val record: WorkspaceActivityRecord, val indent: Int,
        var visible: Boolean? = null, var invalid: Boolean = false)
    private data class TaskContext(var value: WorkspaceTask, val token: String, val indent: Int,
        val legacy: Boolean, var valid: Boolean, val bounds: BoundsContext = BoundsContext(),
        val activities: MutableList<WorkspaceActivityRecord> = mutableListOf(), var activity: ActivityContext? = null)

    fun canonicalComponent(component: String): String {
        val parts = component.split('/')
        require(parts.size == 2)
        val pkg = parts[0]
        require(pkg.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")))
        val activity = if (parts[1].startsWith('.')) pkg + parts[1] else parts[1]
        require(activity.matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*")))
        return "$pkg/$activity"
    }

    fun parse(dump: String): List<WorkspaceTask> = observe(dump).map { it.task }

    /** Both correlation paths consume the same bounded structural evidence. */
    internal fun observe(dump: String): List<WorkspaceTaskRecord> {
        val tasks = mutableListOf<WorkspaceTaskRecord>()
        var display = -1
        var stack: StackContext? = null
        var pending: PreambleContext? = null
        var task: TaskContext? = null
        fun finishActivity() {
            val context = task ?: return
            context.activity?.let { activity ->
                if (activity.invalid) context.valid = false
                context.activities += activity.record.copy(visible = activity.visible)
            }
            context.activity = null
        }
        fun finishTask() {
            finishActivity()
            task?.let { context ->
                val matching = context.activities.filter { it.userId == context.value.userId &&
                    it.taskId == context.value.id && it.component == context.value.component }
                val valid = context.valid && !context.bounds.invalid && matching.size <= 1
                val observed = context.value.copy(bounds = if (context.legacy) context.value.bounds else context.bounds.value,
                    visible = if (context.legacy) matching.singleOrNull()?.visible == true else context.value.visible)
                tasks += WorkspaceTaskRecord(if (valid) observed else observed.copy(bounds = null, visible = false),
                    context.token, context.activities.toList(), valid)
            }
            task = null
        }
        fun acceptBounds(context: BoundsContext, line: String) {
            val values = boundsPattern.matchEntire(line)?.groupValues?.drop(1)?.map { it.toIntOrNull() }
            val bounds = if (values != null && values.all { it != null })
                runCatching { PixelBounds(values[0]!!, values[1]!!, values[2]!!, values[3]!!) }.getOrNull() else null
            if (bounds == null || (context.value != null && context.value != bounds)) context.invalid = true
            else if (!context.invalid) context.value = bounds
        }
        dump.lineSequence().takeWhile { !it.trim().startsWith("ActivityTaskSupervisor state:") }.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach
            val indent = line.indexOfFirst { !it.isWhitespace() }
            when {
                trimmed.startsWith("Display #") -> {
                    finishTask(); stack = null; pending = null
                    display = displayPattern.matchEntire(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                }
                trimmed.startsWith("Stack #") -> {
                    finishTask(); pending = null
                    stack = stackPattern.matchEntire(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let { id ->
                        StackContext(id, modePattern.findAll(trimmed).map { it.groupValues[1] }.singleOrNull())
                    }
                }
                trimmed.startsWith("Task id") -> {
                    finishTask()
                    pending = legacyTaskIdPattern.matchEntire(trimmed)?.groupValues?.get(1)?.toIntOrNull()?.let {
                        PreambleContext(it, indent)
                    }
                }
                trimmed.startsWith("Running activities (") -> {
                    finishTask(); pending = null
                }
                taskBoundaryPattern.containsMatchIn(trimmed) -> {
                    finishTask()
                    val preamble = pending
                    pending = null
                    val match = taskPattern.matchEntire(trimmed) ?: return@forEach
                    val id = match.groupValues[2].toIntOrNull() ?: return@forEach
                    val legacy = trimmed.startsWith("* TaskRecord{")
                    val user = userPattern.findAll(trimmed).map { it.groupValues[1].toIntOrNull() }.singleOrNull() ?: -1
                    val stackId = stackIdPattern.findAll(trimmed).map { it.groupValues[1].toIntOrNull() }.singleOrNull()
                    val stackMatches = stack != null && stack!!.mode != null && stackId == stack!!.id
                    val preambleMatches = preamble?.id == id && preamble.indent == indent && !preamble.bounds.invalid
                    task = TaskContext(WorkspaceTask(id, display, user, null,
                        if (legacy && preambleMatches) preamble!!.bounds.value else null,
                        if (legacy) stackMatches && stack!!.mode == "freeform" else trimmed.contains("mode=freeform"),
                        !legacy && Regex("\\bvisible=true\\b").containsMatchIn(trimmed)), match.groupValues[1], indent,
                        legacy, display >= 0 && user >= 0 && (!legacy || (stackMatches && preambleMatches)))
                }
                task == null && pending != null -> {
                    if (indent < pending!!.indent) pending = null
                    else if (indent == pending!!.indent && trimmed.startsWith("mBounds=")) acceptBounds(pending!!.bounds, trimmed)
                }
                task != null -> {
                    val context = task!!
                    if (indent <= context.indent) {
                        finishTask()
                        return@forEach
                    }
                    if (trimmed.startsWith("* Hist") || trimmed.startsWith("Hist") ||
                        trimmed.startsWith("* ActivityRecord{") || trimmed.startsWith("ActivityRecord{")) {
                        finishActivity()
                        val match = activityPattern.matchEntire(trimmed)
                        val user = match?.groupValues?.get(2)?.toIntOrNull()
                        val component = match?.groupValues?.get(3)?.let { runCatching { canonicalComponent(it) }.getOrNull() }
                        val owner = match?.groupValues?.get(4)?.toIntOrNull()
                        if (match == null || user == null || component == null || owner == null) context.valid = false
                        else {
                            if (user != context.value.userId || owner != context.value.id) context.valid = false
                            context.activity = ActivityContext(WorkspaceActivityRecord(match.groupValues[1], user, component, owner, null), indent)
                        }
                        return@forEach
                    }
                    if (context.activity != null && indent <= context.activity!!.indent) finishActivity()
                    if (!context.legacy && context.activity == null && trimmed.startsWith("mBounds=")) acceptBounds(context.bounds, trimmed)
                    if (trimmed.startsWith("Intent {") && context.value.component == null) {
                        context.value = context.value.copy(launchIdentifier=identifierPattern.find(trimmed)?.groupValues?.get(1))
                        componentPattern.find(trimmed)?.groupValues?.get(1)?.let { raw ->
                            context.value = context.value.copy(component = runCatching { canonicalComponent(raw) }.getOrNull())
                        }
                    }
                    if (context.legacy && trimmed.startsWith("keysPaused=")) {
                        val activity = context.activity
                        val values = visibilityPattern.findAll(trimmed).map { it.groupValues[1] == "true" }.toList()
                        if (activity == null) context.valid = false
                        else if (values.isNotEmpty()) {
                            if (values.distinct().size != 1 || (activity.visible != null && activity.visible != values.first())) activity.invalid = true
                            else activity.visible = values.first()
                        } else if (Regex("\\bvisible=").containsMatchIn(trimmed)) activity.invalid = true
                    }
                }
            }
        }
        finishTask()
        // Repeated object tokens cannot prove one exact ActivityRecord owner, even across tasks/displays.
        val duplicateTokens = tasks.flatMap { it.activities }.groupingBy { it.token }.eachCount().filterValues { it > 1 }.keys
        return tasks.map { record ->
            if (record.activities.any { it.token in duplicateTokens })
                record.copy(task = record.task.copy(bounds = null, visible = false), valid = false) else record
        }
    }

        const val TOLERANCE_PX = 2
        val DUMP_COMMAND = listOf("dumpsys","activity","activities")
        fun sameIdentity(a: WorkspaceTask,b: WorkspaceTask): Boolean = a.id == b.id &&
            a.component == b.component && a.displayId == b.displayId && a.userId == b.userId &&
            a.launchIdentifier == b.launchIdentifier && b.freeform && b.visible
        fun withinTolerance(expected: PixelBounds,actual: PixelBounds?): Boolean = actual != null &&
            abs(expected.left-actual.left)<=TOLERANCE_PX && abs(expected.top-actual.top)<=TOLERANCE_PX &&
            abs(expected.right-actual.right)<=TOLERANCE_PX && abs(expected.bottom-actual.bottom)<=TOLERANCE_PX
}
