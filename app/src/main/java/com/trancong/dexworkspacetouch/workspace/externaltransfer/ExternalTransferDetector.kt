package com.trancong.dexworkspacetouch.workspace.externaltransfer

import com.trancong.dexworkspacetouch.workspace.librarytransfer.WorkspaceLibraryTransferFormat
import com.trancong.dexworkspacetouch.workspace.transfer.WorkspaceTransferFormat
import java.io.ByteArrayOutputStream
import java.io.InputStream

object ExternalTransferDetector {
    fun detect(bytes: ByteArray): ExternalTransferDetection {
        if (bytes.size > WorkspaceLibraryTransferFormat.MaxBytes) return ExternalTransferDetection.FileTooLarge
        val source = bytes.toString(Charsets.UTF_8)
        val singlePrefix = "{\"format\":\"dex-workspace-touch\",\"formatVersion\":"
        val bundlePrefix = "{\"format\":\"dex-workspace-touch-library\",\"formatVersion\":"
        val prefix = when {
            source.startsWith(singlePrefix) -> singlePrefix
            source.startsWith(bundlePrefix) -> bundlePrefix
            else -> return ExternalTransferDetection.Invalid
        }
        var end = prefix.length
        while (end < source.length && source[end].isDigit()) end++
        val version = source.substring(prefix.length, end).toIntOrNull()
            ?: return ExternalTransferDetection.Invalid
        if (version != 1) return ExternalTransferDetection.UnsupportedVersion
        return if (prefix == singlePrefix) {
            if (bytes.size > WorkspaceTransferFormat.MaxBytes) ExternalTransferDetection.FileTooLarge
            else ExternalTransferDetection.SingleWorkspace
        } else ExternalTransferDetection.LibraryBundle
    }
}

fun readExternalTransferBytes(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > WorkspaceLibraryTransferFormat.MaxBytes) throw ExternalTransferTooLargeException()
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

class ExternalTransferTooLargeException : Exception()
