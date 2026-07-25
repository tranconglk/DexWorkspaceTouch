package com.trancong.dexworkspacetouch.about.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

fun copyDiagnosticText(context: Context, text: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("DexWorkspaceTouch diagnostics", text))
}.isSuccess
