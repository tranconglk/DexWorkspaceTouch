package com.trancong.dexworkspacetouch.feature.car

sealed interface CarAction {
    data class LaunchApp(val packageName: String) : CarAction {
        init {
            require(packageName.isNotBlank()) { "Package name must not be blank." }
        }
    }

    data class OpenUri(val uri: String) : CarAction {
        init {
            require(uri.isNotBlank()) { "URI must not be blank." }
        }
    }

    data class Delay(val durationMillis: Long) : CarAction {
        init {
            require(durationMillis >= 0L) { "Delay duration must not be negative." }
        }
    }

    data class Workspace(val workspaceId: String) : CarAction {
        init {
            require(workspaceId.isNotBlank()) { "Workspace ID must not be blank." }
        }
    }
}
