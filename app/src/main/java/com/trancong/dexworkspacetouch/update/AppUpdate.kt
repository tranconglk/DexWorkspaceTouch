package com.trancong.dexworkspacetouch.update

data class AppUpdate(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val apkSha256: String,
    val apkSize: Long,
    val releaseNotes: String,
)

sealed interface AppUpdateResult {
    data class Available(val update: AppUpdate) : AppUpdateResult
    data object Current : AppUpdateResult
    data object Unavailable : AppUpdateResult
}

fun interface AppUpdateRepository {
    suspend fun check(): AppUpdateResult
}
