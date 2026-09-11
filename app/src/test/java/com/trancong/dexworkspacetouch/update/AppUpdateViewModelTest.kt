package com.trancong.dexworkspacetouch.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun repositoryFailureBecomesUnavailable() = runTest(dispatcher) {
        val viewModel = AppUpdateViewModel(AppUpdateRepository { AppUpdateResult.Unavailable })
        assertEquals(AppUpdateUiState.Idle, viewModel.state)
        viewModel.check()
        assertEquals(AppUpdateUiState.Checking, viewModel.state)
        advanceUntilIdle()
        assertEquals(AppUpdateUiState.Unavailable, viewModel.state)
    }

    @Test fun concurrentCheckIsIgnored() = runTest(dispatcher) {
        var calls = 0
        val viewModel = AppUpdateViewModel(AppUpdateRepository { calls++; AppUpdateResult.Current })
        viewModel.check()
        viewModel.check()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(AppUpdateUiState.Current, viewModel.state)
    }

    @Test fun onlyHttpsAvailableStateExposesDownloadUrl() {
        val valid = AppUpdateUiState.Available(update("https://updates.example.com/app.apk"))
        assertEquals("https://updates.example.com/app.apk", validatedUpdateDownloadUrl(valid))
        assertNull(validatedUpdateDownloadUrl(AppUpdateUiState.Available(update("http://updates.example.com/app.apk"))))
        assertNull(validatedUpdateDownloadUrl(AppUpdateUiState.Current))
    }

    private fun update(url: String) = AppUpdate("2", 2, url, "a".repeat(64), 1, "")
}
