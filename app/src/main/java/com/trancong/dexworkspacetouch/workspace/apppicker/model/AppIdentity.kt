package com.trancong.dexworkspacetouch.workspace.apppicker.model

data class AppIdentity(
    val packageName: String,
    val activityName: String?,
) {
    init {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(activityName == null || activityName.isNotBlank()) {
            "activityName must be null or non-blank"
        }
    }
}

fun AppIdentity.toStableKey(): String = "$packageName#${activityName.orEmpty()}"
