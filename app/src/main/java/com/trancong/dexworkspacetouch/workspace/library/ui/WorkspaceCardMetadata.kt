package com.trancong.dexworkspacetouch.workspace.library.ui

import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WorkspaceCardMetadata(val cellCount: Int, val assignedCount: Int) {
    val compactText: String get() = "$cellCount ô • $assignedCount ứng dụng"
}

fun WorkspaceCanvas.cardMetadata(): WorkspaceCardMetadata = WorkspaceCardMetadata(
    cellCount = cells.size,
    assignedCount = cells.count { it.app != null },
)

object WorkspaceRelativeTimeFormatter {
    fun format(updatedAtEpochMillis: Long, nowEpochMillis: Long): String {
        val elapsed = (nowEpochMillis - updatedAtEpochMillis).coerceAtLeast(0L)
        return when {
            elapsed < MINUTE_MILLIS -> "Vừa cập nhật"
            elapsed < HOUR_MILLIS -> "${elapsed / MINUTE_MILLIS} phút trước"
            elapsed < DAY_MILLIS -> "${elapsed / HOUR_MILLIS} giờ trước"
            elapsed < 2 * DAY_MILLIS -> "Hôm qua"
            elapsed < OLD_DATE_THRESHOLD_MILLIS -> "${elapsed / DAY_MILLIS} ngày trước"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.format(Date(updatedAtEpochMillis.coerceAtLeast(0L)))
        }
    }

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
    private const val OLD_DATE_THRESHOLD_MILLIS = 7 * DAY_MILLIS
}
