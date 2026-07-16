package com.trancong.dexworkspacetouch.workspace.apppicker.model

data class DemoApp(
    val packageName: String,
    val activityName: String,
    val label: String,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(activityName.isNotBlank()) { "activityName must not be blank" }
        require(label.isNotBlank()) { "label must not be blank" }
    }
}
