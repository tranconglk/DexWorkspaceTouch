package com.trancong.dexworkspacetouch.about.presentation

enum class AppDisplayMode {
    PHONE,
    EXTERNAL_DISPLAY,
    UNKNOWN,
}

data class AppDiagnosticInfo(
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val buildChannel: String,
    val buildCommit: String,
    val buildDateUtc: String,
    val databaseVersion: Int,
    val workspaceTransferVersion: Int,
    val libraryBundleVersion: Int,
    val androidSdk: Int,
    val manufacturer: String,
    val model: String,
    val isDexDisplay: Boolean?,
    val currentDisplayId: Int?,
    val appDisplayMode: AppDisplayMode,
)
