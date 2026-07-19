package com.trancong.dexworkspacetouch.workspace.librarytransfer

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun libraryBackupFileName(epochMillis: Long): String =
    "DexWorkspaceTouch-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date(epochMillis.coerceAtLeast(0L)))}.${WorkspaceLibraryTransferFormat.Extension}"

fun selectedWorkspaceBundleFileName(count: Int, epochMillis: Long): String {
    require(count > 0) { "Selected workspace count must be positive" }
    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT)
        .format(Date(epochMillis.coerceAtLeast(0L)))
    return "DexWorkspaceTouch-selected-$count-$timestamp.${WorkspaceLibraryTransferFormat.Extension}"
}

fun readWorkspaceLibraryBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > WorkspaceLibraryTransferFormat.MaxBytes) {
            throw WorkspaceLibraryTransferException(WorkspaceLibraryTransferFailure.FILE_TOO_LARGE)
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
