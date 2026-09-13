package com.trancong.dexworkspacetouch.workspace.launcher.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WorkspaceLaunchDiagnosticExport(val fileName: String, val bytes: ByteArray)

fun AndroidWorkspaceLaunchDiagnostics.prepareLatestExport(): WorkspaceLaunchDiagnosticExport? {
    val report = latestReport() ?: return null
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date())
    return WorkspaceLaunchDiagnosticExport(
        "DexWorkspaceTouch-diagnostic-$timestamp.json",
        report.toByteArray(Charsets.UTF_8),
    )
}
