package com.trancong.dexworkspacetouch.about.presentation

fun formatAppDiagnostics(info: AppDiagnosticInfo): String = buildString {
    appendLine(info.appName.trim().ifEmpty { "DexWorkspaceTouch" })
    appendLine()
    append("Version: ").append(info.versionName).append(" (").append(info.versionCode).appendLine(")")
    append("Channel: ").appendLine(info.buildChannel.fallback())
    append("Build commit: ").appendLine(info.buildCommit.fallback())
    append("Build date: ").appendLine(info.buildDateUtc.fallback())
    append("Database: ").appendLine(info.databaseVersion)
    append("Transfer: dwt=").append(info.workspaceTransferVersion)
        .append(", dwtbundle=").appendLine(info.libraryBundleVersion)
    append("Android SDK: ").appendLine(info.androidSdk)
    append("Device: ").appendLine(formatDevice(info.manufacturer, info.model))
    append("Display: ").append(displayLabel(info.appDisplayMode))
    info.currentDisplayId?.let { append(" (ID ").append(it).append(')') }
}

fun displayModeFor(displayId: Int?, defaultDisplayId: Int = 0): AppDisplayMode = when {
    displayId == null -> AppDisplayMode.UNKNOWN
    displayId == defaultDisplayId -> AppDisplayMode.PHONE
    else -> AppDisplayMode.EXTERNAL_DISPLAY
}

fun displayLabel(mode: AppDisplayMode): String = when (mode) {
    AppDisplayMode.PHONE -> "Phone display"
    AppDisplayMode.EXTERNAL_DISPLAY -> "External display"
    AppDisplayMode.UNKNOWN -> "Unknown"
}

fun formatDevice(manufacturer: String, model: String): String {
    val parts = listOf(manufacturer.trim(), model.trim()).filter(String::isNotEmpty).distinct()
    return parts.joinToString(" ").ifEmpty { "unknown" }
}

private fun String.fallback(): String = trim().ifEmpty { "unknown" }
