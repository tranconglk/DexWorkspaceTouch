package com.trancong.dexworkspacetouch.feature.car.overlay

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidCarOverlayPermissionTest {
    @Test
    fun permissionIntentTargetsCurrentApplicationOverlaySettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = createCarOverlayPermissionIntent(context)

        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}
