package com.trancong.dexworkspacetouch.workspace.transfer

import android.content.Intent
import android.content.ClipData
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.workspace.externaltransfer.AndroidExternalTransferIntentAdapter
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferIntentParser
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferIntentResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WorkspaceFileProviderTest {
    @Test fun exportSubdirectoryProducesReadableContentUri() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "exports/provider-test.dwt")
        file.parentFile!!.mkdirs()
        file.writeText("{}")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        assertEquals("content", uri.scheme)
        context.contentResolver.openInputStream(uri)!!.use { assertEquals("{}", it.reader().readText()) }
        assertTrue(file.delete())
    }

    @Test fun libraryBundleUriIsReadableThroughFileProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "exports/library").apply { mkdirs() }
        val file = File(directory, "backup.dwtbundle").apply { writeText("bundle") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        assertEquals("bundle", context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() })
    }

    @Test fun selectedLibraryBundleUriSupportsShareAndReadBack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "exports/library").apply { mkdirs() }
        val file = File(directory, "DexWorkspaceTouch-selected-4-20260719-0930.dwtbundle")
            .apply { writeText("selected-bundle") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.dexworkspacetouch.library+json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        assertTrue(context.packageManager.queryIntentActivities(intent, 0).isNotEmpty())
        context.contentResolver.openInputStream(uri)!!.use {
            assertEquals("selected-bundle", it.bufferedReader().readText())
        }
        assertTrue(file.delete())
    }

    @Test fun workspaceCustomMimeActionViewResolvesToMainActivity() {
        assertCustomMimeResolves(
            fileName = "open-with-test.dwt",
            mimeType = "application/vnd.dexworkspacetouch.workspace+json",
        )
    }

    @Test fun libraryCustomMimeActionViewResolvesToMainActivity() {
        assertCustomMimeResolves(
            fileName = "open-with-test.dwtbundle",
            mimeType = "application/vnd.dexworkspacetouch.library+json",
        )
    }

    @Test fun downloadsOctetStreamFallbackResolvesToMainActivity() {
        assertCustomMimeResolves(
            fileName = "downloads-fallback.dwt",
            mimeType = "application/octet-stream",
        )
    }

    @Test fun legacyMimeLessWorkspaceExtensionResolvesToMainActivity() {
        assertExtensionOnlyResolves("legacy-open.dwt")
    }

    @Test fun legacyMimeLessBundleExtensionResolvesToMainActivity() {
        assertExtensionOnlyResolves("legacy-open.dwtbundle")
    }

    @Test fun actionSendOctetStreamResolvesAndExtraStreamIsAdapted() {
        val uri = createProviderUri("shared-workspace.dwt")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        assertMainActivityHandles(intent)
        assertEquals(
            ExternalTransferIntentResult.SingleUri(uri.toString()),
            ExternalTransferIntentParser.parse(AndroidExternalTransferIntentAdapter.from(intent)),
        )
    }

    @Test fun actionSendClipDataOnlyIsAdaptedAndReadable() {
        val uri = createProviderUri("shared-library.dwtbundle")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.dexworkspacetouch.library+json"
            clipData = ClipData.newUri(context.contentResolver, "bundle", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        assertMainActivityHandles(intent)
        assertEquals(
            ExternalTransferIntentResult.SingleUri(uri.toString()),
            ExternalTransferIntentParser.parse(AndroidExternalTransferIntentAdapter.from(intent)),
        )
        context.contentResolver.openInputStream(uri)!!.use { assertNotNull(it.readBytes()) }
    }

    private fun createProviderUri(fileName: String): Uri {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName).apply { writeText("{}") }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun assertMainActivityHandles(intent: Intent) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val handlers = context.packageManager.queryIntentActivities(intent, 0)
        assertTrue(handlers.any { it.activityInfo.name == "com.trancong.dexworkspacetouch.MainActivity" })
    }

    private fun assertExtensionOnlyResolves(fileName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName).apply { writeText("{}") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val handlers = context.packageManager.queryIntentActivities(intent, 0)

        assertTrue(handlers.any { it.activityInfo.name == "com.trancong.dexworkspacetouch.MainActivity" })
        assertTrue(file.delete())
    }

    private fun assertCustomMimeResolves(fileName: String, mimeType: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName).apply { writeText("{}") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val handlers = context.packageManager.queryIntentActivities(intent, 0)

        assertTrue(handlers.any { it.activityInfo.name == "com.trancong.dexworkspacetouch.MainActivity" })
        context.contentResolver.openInputStream(uri)!!.use { assertNotNull(it.readBytes()) }
        assertTrue(file.delete())
    }
}
