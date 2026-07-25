package com.trancong.dexworkspacetouch.about.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDiagnosticFormatterTest {
    @Test
    fun `version includes name and numeric code`() {
        assertTrue(formatAppDiagnostics(info()).contains("Version: 1.0.0-beta.2 (3)"))
    }

    @Test
    fun `blank commit falls back to unknown`() {
        assertTrue(formatAppDiagnostics(info(buildCommit = "  ")).contains("Build commit: unknown"))
    }

    @Test
    fun `blank build date falls back to unknown`() {
        assertTrue(formatAppDiagnostics(info(buildDateUtc = "")).contains("Build date: unknown"))
    }

    @Test
    fun `phone display is formatted`() {
        assertTrue(formatAppDiagnostics(info(mode = AppDisplayMode.PHONE)).contains("Display: Phone display"))
    }

    @Test
    fun `external display is formatted without claiming Dex`() {
        val output = formatAppDiagnostics(info(mode = AppDisplayMode.EXTERNAL_DISPLAY))
        assertTrue(output.contains("Display: External display"))
        assertFalse(output.contains("DeX: true"))
    }

    @Test
    fun `unknown display is formatted`() {
        assertTrue(formatAppDiagnostics(info(mode = AppDisplayMode.UNKNOWN)).contains("Display: Unknown"))
    }

    @Test
    fun `manufacturer and model are trimmed`() {
        assertEquals("Samsung SM-S918B", formatDevice(" Samsung ", " SM-S918B "))
    }

    @Test
    fun `formatter contains no unique device identifier field`() {
        val output = formatAppDiagnostics(info())
        assertFalse(output.contains("serial", ignoreCase = true))
        assertFalse(output.contains("android id", ignoreCase = true))
        assertFalse(output.contains("imei", ignoreCase = true))
    }

    @Test
    fun `formatter contains no workspace or installed app data`() {
        val output = formatAppDiagnostics(info())
        assertFalse(output.contains("workspace name", ignoreCase = true))
        assertFalse(output.contains("package list", ignoreCase = true))
        assertFalse(output.contains("content://", ignoreCase = true))
    }

    @Test
    fun `output is deterministic`() {
        assertEquals(formatAppDiagnostics(info()), formatAppDiagnostics(info()))
    }

    @Test
    fun `display mode mapping uses only display id`() {
        assertEquals(AppDisplayMode.PHONE, displayModeFor(0))
        assertEquals(AppDisplayMode.EXTERNAL_DISPLAY, displayModeFor(2))
        assertEquals(AppDisplayMode.UNKNOWN, displayModeFor(null))
    }

    @Test
    fun `release versions use established constants`() {
        assertEquals(2, ReleaseFormatVersions.Database)
        assertEquals(1, ReleaseFormatVersions.WorkspaceTransfer)
        assertEquals(1, ReleaseFormatVersions.LibraryBundle)
    }

    @Test
    fun `long version code is formatted without truncation`() {
        assertTrue(formatAppDiagnostics(info(versionCode = 4_294_967_296L)).contains("(4294967296)"))
    }

    private fun info(
        buildCommit: String = "abc1234",
        buildDateUtc: String = "2026-07-19T12:00:00Z",
        mode: AppDisplayMode = AppDisplayMode.PHONE,
        versionCode: Long = 3,
    ) = AppDiagnosticInfo(
        appName = "DexWorkspaceTouch",
        versionName = "1.0.0-beta.2",
        versionCode = versionCode,
        buildChannel = "beta",
        buildCommit = buildCommit,
        buildDateUtc = buildDateUtc,
        databaseVersion = 2,
        workspaceTransferVersion = 1,
        libraryBundleVersion = 1,
        androidSdk = 34,
        manufacturer = "Samsung",
        model = "SM-S918B",
        isDexDisplay = null,
        currentDisplayId = 0,
        appDisplayMode = mode,
    )
}
