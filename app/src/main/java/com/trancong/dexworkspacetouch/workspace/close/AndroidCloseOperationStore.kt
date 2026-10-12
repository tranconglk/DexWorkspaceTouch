package com.trancong.dexworkspacetouch.workspace.close

import android.content.Context
import android.system.Os
import android.system.OsConstants
import java.io.File

fun createAndroidCloseOperationStore(context: Context): FileCloseOperationStore =
    FileCloseOperationStore(File(context.filesDir, "close-operations")) { directory ->
        check(directory.isDirectory) { "JOURNAL_DIRECTORY_REQUIRED" }
        val descriptor = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(descriptor) } finally { Os.close(descriptor) }
    }
