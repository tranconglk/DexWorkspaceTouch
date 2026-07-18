package com.trancong.dexworkspacetouch.workspace.transfer

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidWorkspaceTransferPlatform(private val activity: Activity) {
    suspend fun share(ready: WorkspaceTransferState.ExportReady) {
        val uri = try { withContext(Dispatchers.IO) {
            val directory = File(activity.cacheDir, "exports").apply { mkdirs() }
            cleanup(directory)
            val target = File(directory, ready.fileName)
            atomicWrite(target, ready.bytes)
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", target)
        } } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Preparing FileProvider URI failed: ${error.javaClass.simpleName}", error)
            throw error
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = WorkspaceTransferFormat.MimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(activity.contentResolver, ready.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (activity.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
            intent.type = "application/json"
        }
        if (activity.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
            intent.type = "*/*"
        }
        Log.d(TAG, "Opening Sharesheet with MIME ${intent.type}")
        try {
            activity.startActivity(Intent.createChooser(intent, "Chia sẻ workspace"))
        } catch (error: Exception) {
            Log.e(TAG, "Opening Sharesheet failed: ${error.javaClass.simpleName}", error)
            throw error
        }
    }

    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            activity.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) }
                ?: throw WorkspaceTransferException(WorkspaceTransferFailure.WRITE_FAILURE)
        } catch (error: WorkspaceTransferException) { throw error }
        catch (error: Exception) { throw WorkspaceTransferException(WorkspaceTransferFailure.WRITE_FAILURE, error) }
    }

    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        try {
            activity.contentResolver.openInputStream(uri)?.use(::readWorkspaceBytes)
                ?: throw WorkspaceTransferException(WorkspaceTransferFailure.READ_FAILURE)
        } catch (error: WorkspaceTransferException) { throw error }
        catch (error: Exception) { throw WorkspaceTransferException(WorkspaceTransferFailure.READ_FAILURE, error) }
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        try {
            temporary.outputStream().use { it.write(bytes) }
            if (target.exists() && !target.delete()) throw java.io.IOException("Cannot replace export")
            if (!temporary.renameTo(target)) throw java.io.IOException("Cannot finalize export")
        } finally { if (temporary.exists()) temporary.delete() }
    }

    private fun cleanup(directory: File) {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        val cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS
        files.forEachIndexed { index, file -> if (index >= MAX_FILES - 1 || file.lastModified() < cutoff) file.delete() }
        directory.listFiles()?.filter { it.name.endsWith(".tmp") }?.forEach(File::delete)
    }

    private companion object {
        const val TAG = "WorkspaceTransfer"
        const val MAX_FILES = 10
        const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}
