package com.trancong.dexworkspacetouch.feature.car.overlay

import com.trancong.dexworkspacetouch.platform.launch.shizuku.ExistingWorkspaceRepairReport
import com.trancong.dexworkspacetouch.platform.launch.shizuku.ExistingWorkspaceAssessmentReport
import com.trancong.dexworkspacetouch.platform.launch.shizuku.RepairCellStatus
import com.trancong.dexworkspacetouch.workspace.launcher.model.WorkspaceLaunchRequest
import kotlinx.coroutines.flow.StateFlow

data class CarDockRepairState(val label: String = "Repair unavailable", val enabled: Boolean = false,
    val detail: String = "Open a workspace first", val report: ExistingWorkspaceRepairReport? = null,
    val assessment: ExistingWorkspaceAssessmentReport? = null, val workspaceId: String? = null,
    val shizukuState: com.trancong.dexworkspacetouch.platform.launch.shizuku.ShizukuRuntimeState? = null) {
    fun control(onRepair: () -> Unit) = CarDockRepairControl(label,enabled,detail,onRepair)
}
data class CarDockRepairControl(val label: String, val enabled: Boolean, val detail: String, val onRepair: () -> Unit)

fun ExistingWorkspaceRepairReport.toDockState(): CarDockRepairState {
    val correct = cells.count { it.status == RepairCellStatus.CORRECT }
    val repaired = cells.count { it.status == RepairCellStatus.REPAIRED }
    val detail = when {
        !admitted -> "Workspace control busy; Repair unavailable"
        complete && repaired == 0 -> "Layout already correct"
        complete -> "$repaired repaired, $correct correct"
        else -> "Partial: $repaired repaired, $correct correct; " + cells.filter {
            it.status != RepairCellStatus.CORRECT && it.status != RepairCellStatus.REPAIRED
        }.joinToString("; ") { "${it.cellId}: ${it.status}" }
    }
    return CarDockRepairState(when {
        !admitted -> "Repair unavailable"
        complete && repaired > 0 -> "\u2713 Repaired"
        complete -> "Repair"
        else -> "Repair: ${repaired + correct}/${cells.size}"
    }, true, detail, this)
}
interface ManualWorkspaceRepairController {
    val state: StateFlow<CarDockRepairState>
    fun selectWorkspace(workspaceId: String)
    fun classicLaunchStarted(workspaceId: String) { selectWorkspace(workspaceId) }
    fun classicLaunchCompleted(request: WorkspaceLaunchRequest) {}
    fun controlActionStarted() {}
    fun invalidateSuggestion() {}
    fun repair()
    fun dispose()
}
