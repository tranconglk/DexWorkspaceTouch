package com.trancong.dexworkspacetouch.branding

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandingResourceContractTest {
    private val main = File("src/main")

    @Test
    fun `manifest uses production launcher and starting theme`() {
        val manifest = main.resolve("AndroidManifest.xml").readText()

        assertTrue(manifest.contains("""android:icon="@mipmap/ic_launcher""""))
        assertTrue(manifest.contains("""android:roundIcon="@mipmap/ic_launcher_round""""))
        assertTrue(manifest.contains("""android:theme="@style/Theme.DexWorkspaceTouch.Starting""""))
        assertFalse(manifest.contains("sym_def_app_icon"))
    }

    @Test
    fun `adaptive icon exposes foreground background and monochrome`() {
        val adaptive = main.resolve("res/mipmap-anydpi-v33/ic_launcher.xml").readText()

        assertTrue(adaptive.contains("@color/ic_launcher_background"))
        assertTrue(adaptive.contains("@drawable/ic_launcher_foreground"))
        assertTrue(adaptive.contains("@drawable/ic_launcher_monochrome"))
    }

    @Test
    fun `all legacy density buckets contain regular and round icons`() {
        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { density ->
            val directory = main.resolve("res/mipmap-$density")
            assertTrue(directory.resolve("ic_launcher.png").isFile)
            assertTrue(directory.resolve("ic_launcher_round.png").isFile)
        }
    }

    @Test
    fun `api 31 starting theme uses system splash and returns to app theme`() {
        val theme = main.resolve("res/values-v31/themes.xml").readText()

        assertTrue(theme.contains("windowSplashScreenBackground"))
        assertTrue(theme.contains("windowSplashScreenAnimatedIcon"))
        assertTrue(theme.contains("postSplashScreenTheme"))
    }
}
