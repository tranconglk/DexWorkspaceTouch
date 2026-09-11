package com.trancong.dexworkspacetouch.update

import com.trancong.dexworkspacetouch.ui.design.DwtStatusTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePresentationTest {
    @Test fun updateStatesHaveDistinctSemanticPresentation() {
        assertEquals(DwtStatusTone.Info, AppUpdateUiState.Idle.presentation().tone)
        assertTrue(AppUpdateUiState.Checking.presentation().showProgress)
        assertEquals(DwtStatusTone.Success, AppUpdateUiState.Current.presentation().tone)
        assertEquals(DwtStatusTone.Warning, AppUpdateUiState.Unavailable.presentation().tone)
    }

    @Test fun availableStateExposesDownloadOnlyForValidatedHttpsUrl() {
        val valid = AppUpdateUiState.Available(update("https://updates.example.com/app.apk"))
        val invalid = AppUpdateUiState.Available(update("http://updates.example.com/app.apk"))

        assertTrue(valid.presentation().showDownloadAction)
        assertFalse(invalid.presentation().showDownloadAction)
        assertEquals("Có phiên bản mới 2.0", valid.presentation().title)
    }

    private fun update(url: String) = AppUpdate(
        versionName = "2.0",
        versionCode = 2,
        apkUrl = url,
        apkSha256 = "a".repeat(64),
        apkSize = 1,
        releaseNotes = "Release notes",
    )
}
