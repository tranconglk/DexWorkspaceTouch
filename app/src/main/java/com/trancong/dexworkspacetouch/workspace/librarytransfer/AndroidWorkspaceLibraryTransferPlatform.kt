package com.trancong.dexworkspacetouch.workspace.librarytransfer

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidWorkspaceLibraryTransferPlatform(private val activity: Activity) {
    suspend fun share(ready: WorkspaceLibraryTransferState.BackupReady) {
        val uri = withContext(Dispatchers.IO) {
            val directory = File(activity.cacheDir, "exports/library").apply { mkdirs() }
            cleanup(directory)
            val target = File(directory, ready.fileName)
            atomicWrite(target, ready.bytes)
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", target)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = WorkspaceLibraryTransferFormat.MimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(activity.contentResolver, ready.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (activity.packageManager.queryIntentActivities(intent, 0).isEmpty()) intent.type = "application/json"
        if (activity.packageManager.queryIntentActivities(intent, 0).isEmpty()) intent.type = "*/*"
        val chooserTitle = if (ready.selectedExport) "Chia sẻ ${ready.workspaceCount} workspace"
        else "Chia sẻ bản sao lưu Library"
        activity.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try { activity.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
            ?: throw WorkspaceLibraryTransferException(WorkspaceLibraryTransferFailure.WRITE_FAILURE) }
        catch (error: WorkspaceLibraryTransferException) { throw error }
        catch (error: Exception) { throw WorkspaceLibraryTransferException(WorkspaceLibraryTransferFailure.WRITE_FAILURE, error) }
    }

    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try { activity.contentResolver.openInputStream(uri)?.use(::readWorkspaceLibraryBytes)
            ?: throw WorkspaceLibraryTransferException(WorkspaceLibraryTransferFailure.READ_FAILURE) }
        catch (error: WorkspaceLibraryTransferException) { throw error }
        catch (error: Exception) { throw WorkspaceLibraryTransferException(WorkspaceLibraryTransferFailure.READ_FAILURE, error) }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            temporary.outputStream().use { it.write(bytes) }
            if (target.exists() && !target.delete()) throw java.io.IOException("Cannot replace library backup")
            if (!temporary.renameTo(target)) throw java.io.IOException("Cannot finalize library backup")
        } finally { if (temporary.exists()) temporary.delete() }
    }

    private fun cleanup(directory: File) {
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        directory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach(File::delete)
        directory.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
            .forEachIndexed { index, file -> if (index >= MAX_FILES - 1 || file.lastModified() < cutoff) file.delete() }
    }

    private companion object {
        const val MAX_FILES = 5
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
