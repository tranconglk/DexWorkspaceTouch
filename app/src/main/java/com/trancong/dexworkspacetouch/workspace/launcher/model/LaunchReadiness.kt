package com.trancong.dexworkspacetouch.workspace.launcher.model

import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import com.trancong.dexworkspacetouch.workspace.designer.model.CanvasValidationIssue

sealed interface LaunchReadiness {
    data class Ready(val request: WorkspaceLaunchRequest) : LaunchReadiness

    data object EmptyWorkspace : LaunchReadiness

    data class EmptyCells(val cellIds: List<String>) : LaunchReadiness {
        init {
            require(cellIds.isNotEmpty()) { "cellIds must not be empty" }
        }
    }

    data class MissingApplications(val items: List<LaunchApplicationIssue>) : LaunchReadiness {
        init {
            require(items.isNotEmpty()) { "items must not be empty" }
        }
    }

    data class NonLaunchableApplications(
        val items: List<LaunchApplicationIssue>,
    ) : LaunchReadiness {
        init {
            require(items.isNotEmpty()) { "items must not be empty" }
        }
    }

    data class InvalidCanvas(val issues: List<CanvasValidationIssue>) : LaunchReadiness {
        init {
            require(issues.isNotEmpty()) { "issues must not be empty" }
        }
    }
}

data class LaunchApplicationIssue(
    val cellId: String,
    val identity: AppIdentity,
) {
    init {
        require(cellId.isNotBlank()) { "cellId must not be blank" }
    }
}
