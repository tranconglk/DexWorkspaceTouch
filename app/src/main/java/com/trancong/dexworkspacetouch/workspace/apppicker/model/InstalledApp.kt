package com.trancong.dexworkspacetouch.workspace.apppicker.model

data class InstalledApp(
    val packageName: String,
    val activityName: String?,
    val label: String,
    val launchable: Boolean,
    val isSystemApp: Boolean = false,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(activityName == null || activityName.isNotBlank()) {
            "activityName must be null or non-blank"
        }
        require(label.isNotBlank()) { "label must not be blank" }
        require(!launchable || activityName != null) {
            "A launchable app must have an activityName"
        }
    }

    val identity: AppIdentity get() = AppIdentity(packageName, activityName)
}
