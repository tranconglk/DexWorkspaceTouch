package com.trancong.dexworkspacetouch.workspace.launcher.diagnostics

import java.util.Locale

fun formatWorkspaceLaunchDiagnostic(session: WorkspaceLaunchDiagnosticSession): String = buildString {
    appendLine("{")
    field("schemaVersion", "1", false, 2)
    field("sessionId", quoted(session.sessionId), false, 2)
    field("timestampEpochMillis", session.timestampEpochMillis.toString(), false, 2)
    field("appVersionName", quoted(session.appVersionName), false, 2)
    field("appVersionCode", session.appVersionCode.toString(), false, 2)
    appendLine("  \"device\": {")
    field("manufacturer", quoted(session.device.manufacturer), false, 4)
    field("model", quoted(session.device.model), false, 4)
    field("sdkInt", session.device.sdkInt.toString(), true, 4)
    appendLine("  },")
    appendLine("  \"workspace\": {")
    field("workspaceId", quoted(session.workspaceId), false, 4)
    field("workspaceName", quoted(session.workspaceName), false, 4)
    field("appCount", session.appCount.toString(), true, 4)
    appendLine("  },")
    appendLine("  \"apps\": [")
    session.apps.sortedBy { it.sequenceIndex }.forEachIndexed { index, app ->
        appendLine("    {")
        field("sequenceIndex", app.sequenceIndex.toString(), false, 6)
        field("packageName", quoted(app.packageName), false, 6)
        field("activityName", nullable(app.activityName), false, 6)
        field("activityInfo", activityInfo(app.activityInfo), false, 6)
        field("normalizedBounds", normalized(app.normalizedBounds), false, 6)
        field("display", display(app.display), false, 6)
        field("beforeClampBounds", rect(app.beforeClampBounds), false, 6)
        field("requestedPixelBounds", rect(app.requestedPixelBounds), false, 6)
        field("marginPx", app.marginPx?.toString() ?: "null", false, 6)
        field("intentFlags", quoted("0x${app.intentFlags.toUInt().toString(16).uppercase(Locale.ROOT)}"), false, 6)
        field("launchDisplayId", app.launchDisplayId?.toString() ?: "null", false, 6)
        field("launchStartedAt", app.launchStartedAtEpochMillis?.toString() ?: "null", false, 6)
        field("launchCompletedAt", app.launchCompletedAtEpochMillis?.toString() ?: "null", false, 6)
        field("result", quoted(app.result), true, 6)
        append("    }")
        if (index != session.apps.lastIndex) append(',')
        appendLine()
    }
    appendLine("  ],")
    field(
        "limitation",
        quoted("Requested bounds only. Actual third-party task bounds require device observation or ADB/dumpsys."),
        true,
        2,
    )
    appendLine("}")
}

internal fun retainedDiagnosticFilesNewestFirst(namesNewestFirst: List<String>, maximum: Int = 20): List<String> {
    require(maximum >= 0)
    return namesNewestFirst.take(maximum)
}

private fun StringBuilder.field(name: String, value: String, last: Boolean, indent: Int) {
    append(" ".repeat(indent)).append(quoted(name)).append(": ").append(value)
    if (!last) append(',')
    appendLine()
}

private fun quoted(value: String): String = buildString {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
        }
    }
    append('"')
}

private fun nullable(value: String?) = value?.let(::quoted) ?: "null"
private fun number(value: Float) = String.format(Locale.ROOT, "%.6f", value)
private fun normalized(value: com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds) =
    "{\"left\":${number(value.left)},\"top\":${number(value.top)},\"right\":${number(value.right)},\"bottom\":${number(value.bottom)}}"

private fun rect(value: DiagnosticRect?): String = value?.let {
    "{\"left\":${it.left},\"top\":${it.top},\"right\":${it.right},\"bottom\":${it.bottom},\"width\":${it.width},\"height\":${it.height}}"
} ?: "null"

private fun activityInfo(value: WorkspaceLaunchActivityInfo?): String = value?.let {
    "{\"launchMode\":${it.launchMode ?: "null"},\"documentLaunchMode\":${it.documentLaunchMode ?: "null"}," +
        "\"taskAffinity\":${nullable(it.taskAffinity)},\"resizeMode\":${it.resizeMode ?: "null"}}"
} ?: "null"

private fun display(value: WorkspaceLaunchDiagnosticDisplay?): String = value?.let {
    "{\"displayId\":${it.displayId},\"name\":${nullable(it.name)},\"type\":${it.type ?: "null"}," +
        "\"logicalWidth\":${it.logicalWidthPx},\"logicalHeight\":${it.logicalHeightPx}," +
        "\"modeWidth\":${it.modeWidthPx ?: "null"},\"modeHeight\":${it.modeHeightPx ?: "null"}," +
        "\"density\":${number(it.density)},\"workArea\":${rect(it.workArea)}," +
        "\"resolvedInsets\":{\"left\":${it.resolvedInsets.left},\"top\":${it.resolvedInsets.top}," +
        "\"right\":${it.resolvedInsets.right},\"bottom\":${it.resolvedInsets.bottom}}," +
        "\"selectedInsetSource\":${quoted(it.selectedInsetSource)}}"
} ?: "null"
