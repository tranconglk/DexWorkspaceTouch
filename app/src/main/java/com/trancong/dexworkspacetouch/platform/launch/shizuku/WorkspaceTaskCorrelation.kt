package com.trancong.dexworkspacetouch.platform.launch.shizuku

import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds
import kotlin.math.abs

data class WorkspaceCommandResult(val exitCode: Int, val output: String)
fun interface WorkspaceCommandShell { fun execute(arguments: List<String>): WorkspaceCommandResult }
data class WorkspaceTask(val id: Int, val displayId: Int, val userId: Int, val component: String?,
    val bounds: PixelBounds?, val freeform: Boolean, val visible: Boolean, val launchIdentifier: String? = null)

/** Conservative task observation; no task launch or process transport. */
object WorkspaceTaskCorrelation {
    private val displayPattern = Regex("^Display #(\\d+)")
    private val taskPattern = Regex("^\\s*\\* Task\\{[^#]*#(\\d+)\\b.*")
    private val userPattern = Regex("\\bU=(\\d+)\\b")
    private val boundsPattern = Regex("^mBounds=Rect\\((\\d+), (\\d+) - (\\d+), (\\d+)\\)")
    private val componentPattern = Regex("\\bcmp=([^\\s}]+)")
    private val identifierPattern = Regex("\\bid=([^\\s}]+)")

    fun canonicalComponent(component: String): String {
        val parts = component.split('/')
        require(parts.size == 2)
        val pkg = parts[0]
        require(pkg.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+")))
        val activity = if (parts[1].startsWith('.')) pkg + parts[1] else parts[1]
        require(activity.matches(Regex("[A-Za-z_][A-Za-z0-9_.$]*")))
        return "$pkg/$activity"
    }

    fun parse(dump: String): List<WorkspaceTask> {
        val tasks = mutableListOf<WorkspaceTask>()
        var display = -1
        var task: WorkspaceTask? = null
        fun finish() { task?.let(tasks::add); task = null }
        dump.lineSequence().takeWhile { !it.startsWith("ActivityTaskSupervisor state:") }.forEach { line ->
            val displayMatch = displayPattern.find(line)
            val taskMatch = taskPattern.matchEntire(line)
            when {
                displayMatch != null -> { finish(); display = displayMatch.groupValues[1].toInt() }
                taskMatch != null -> {
                    finish()
                    task = WorkspaceTask(taskMatch.groupValues[1].toInt(), display,
                        userPattern.find(line)?.groupValues?.get(1)?.toInt() ?: -1, null, null,
                        line.contains("mode=freeform"), Regex("\\bvisible=true\\b").containsMatchIn(line))
                }
                task != null -> {
                    val trimmed = line.trim()
                    boundsPattern.find(trimmed)?.let { match ->
                        val v = match.groupValues.drop(1).map(String::toInt)
                        task = task!!.copy(bounds = runCatching { PixelBounds(v[0],v[1],v[2],v[3]) }.getOrNull())
                    }
                    if (trimmed.startsWith("Intent {") && task!!.component == null) {
                        task = task!!.copy(launchIdentifier=identifierPattern.find(trimmed)?.groupValues?.get(1))
                        componentPattern.find(trimmed)?.groupValues?.get(1)?.let { raw ->
                            task = task!!.copy(component = runCatching { canonicalComponent(raw) }.getOrNull())
                        }
                    }
                }
            }
        }
        finish()
        return tasks
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
