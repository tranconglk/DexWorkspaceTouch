package com.trancong.dexworkspacetouch.workspace.launcher.diagnostics

import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceLaunchDiagnosticFormatterTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun `report serializes bounds flags and apps in launch sequence`() {
        val report = formatWorkspaceLaunchDiagnostic(session())

        assertTrue(report.contains("\"sessionId\": \"session-1\""))
        assertTrue(report.contains("\"beforeClampBounds\": {\"left\":-4,\"top\":8,\"right\":964,\"bottom\":1192,\"width\":968,\"height\":1184}"))
        assertTrue(report.contains("\"requestedPixelBounds\": {\"left\":8,\"top\":8,\"right\":952,\"bottom\":1192,\"width\":944,\"height\":1184}"))
        assertTrue(report.contains("\"intentFlags\": \"0x18000000\""))
        assertTrue(report.indexOf("pkg.first") < report.indexOf("pkg.second"))
    }

    @Test
    fun `report excludes forbidden secret and device identity fields`() {
        val report = formatWorkspaceLaunchDiagnostic(session()).lowercase()

        listOf("licensekey", "licensetoken", "androidid", "serialnumber", "authorization")
            .forEach { forbidden -> assertFalse(forbidden, report.contains(forbidden)) }
    }

    @Test
    fun `storage retains only twenty newest sessions and exports latest`() {
        val storage = FileWorkspaceLaunchDiagnosticStorage(temporaryFolder.newFolder("diagnostics"))
        (1L..23L).forEach { index ->
            val timestamp = 1_700_000_000_000L + index
            storage.write("session-$index", timestamp, "report-$index")
        }

        assertEquals(20, temporaryFolder.root.resolve("diagnostics").listFiles()!!.size)
        assertEquals("report-23", storage.latest())
        assertTrue(storage.hasReports())
        storage.clear()
        assertFalse(storage.hasReports())
    }

    @Test
    fun `retention helper preserves newest-first order`() {
        assertEquals(listOf("3.json", "2.json"), retainedDiagnosticFilesNewestFirst(
            listOf("3.json", "2.json", "1.json"), maximum = 2,
        ))
    }

    private fun session() = WorkspaceLaunchDiagnosticSession(
        sessionId = "session-1",
        timestampEpochMillis = 123L,
        appVersionName = "1.0",
        appVersionCode = 1L,
        device = WorkspaceLaunchDiagnosticDevice("Samsung", "S23", 36),
        workspaceId = "workspace-1",
        workspaceName = "Test workspace",
        appCount = 2,
        apps = listOf(app(2, "pkg.second"), app(1, "pkg.first")),
    )

    private fun app(sequence: Int, packageName: String) = WorkspaceLaunchDiagnosticApp(
        sequenceIndex = sequence,
        packageName = packageName,
        activityName = "$packageName.MainActivity",
        activityInfo = WorkspaceLaunchActivityInfo(0, 0, packageName, null),
        normalizedBounds = NormalizedBounds(0f, 0f, 0.5f, 1f),
        display = null,
        beforeClampBounds = DiagnosticRect(-4, 8, 964, 1192),
        requestedPixelBounds = DiagnosticRect(8, 8, 952, 1192),
        marginPx = 8,
        intentFlags = 0x18000000,
        launchDisplayId = 2,
        launchStartedAtEpochMillis = 200L,
        launchCompletedAtEpochMillis = 250L,
        result = "SUCCESS",
    )
}
