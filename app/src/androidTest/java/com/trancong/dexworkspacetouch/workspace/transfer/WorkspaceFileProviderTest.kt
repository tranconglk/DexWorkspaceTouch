package com.trancong.dexworkspacetouch.workspace.transfer

import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
}
