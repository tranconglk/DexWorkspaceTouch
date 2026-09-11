package com.trancong.dexworkspacetouch.license.runtime

import com.trancong.dexworkspacetouch.license.activation.LicenseActivationResult
import com.trancong.dexworkspacetouch.license.activation.LicenseRepository
import com.trancong.dexworkspacetouch.license.activation.LicenseRefreshResult
import com.trancong.dexworkspacetouch.license.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LicenseRuntimeCoordinatorTest {
    private val dispatcher = StandardTestDispatcher()
    private val now = 1_000L

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun noStoredTokenRequiresActivation() = runTest(dispatcher) {
        val coordinator = coordinator(FakeRepository())
        runCurrent()
        assertState<LicenseState.Unactivated>(coordinator)
    }

    @Test fun validStoredTokenBecomesOfflineGrace() = runTest(dispatcher) {
        val coordinator = coordinator(FakeRepository("token", LicenseState.Active(claims())))
        runCurrent()
        assertTrue((coordinator.state.value as LicenseGateUiState.Allowed).licenseState is LicenseState.OfflineGrace)
    }

    @Test fun expiredMismatchAndCorruptStoredTokensFailClosed() = runTest(dispatcher) {
        val expired = coordinator(FakeRepository("token", LicenseState.Error(LicenseFailure.Rejected(LicenseErrorCode.TOKEN_EXPIRED))))
        runCurrent(); assertState<LicenseState.Expired>(expired)
        val mismatch = coordinator(FakeRepository("token", LicenseState.Error(LicenseFailure.Rejected(LicenseErrorCode.DEVICE_MISMATCH))))
        runCurrent(); assertState<LicenseState.DeviceMismatch>(mismatch)
        val corrupt = coordinator(FakeRepository("token", LicenseState.Error(LicenseFailure.LocalDataCorrupted)))
        runCurrent(); assertState<LicenseState.Error>(corrupt)
    }

    @Test fun exactEffectiveEndBoundaryIsExpired() = runTest(dispatcher) {
        val coordinator = coordinator(FakeRepository("token", LicenseState.Active(claims(expires = now))))
        runCurrent(); assertState<LicenseState.Expired>(coordinator)
    }

    @Test fun successfulActivationIsActiveAndClearsKeyFromUiState() = runTest(dispatcher) {
        val repo = FakeRepository(activation = LicenseActivationResult.Success(LicenseState.Active(claims()), "request"))
        val coordinator = coordinator(repo); runCurrent()
        coordinator.updateLicenseKey("app-aaaa-bbbb-cccc"); coordinator.activate(); runCurrent()
        val allowed = coordinator.state.value as LicenseGateUiState.Allowed
        assertTrue(allowed.licenseState is LicenseState.Active)
        assertEquals(1, repo.activationCalls)
        assertFalse(coordinator.state.value.toString().contains("APP-AAAA-BBBB-CCCC"))
    }

    @Test fun invalidLocalKeyDoesNotCallRepository() = runTest(dispatcher) {
        val repo = FakeRepository(); val coordinator = coordinator(repo); runCurrent()
        coordinator.updateLicenseKey("invalid"); coordinator.activate(); runCurrent()
        assertEquals(0, repo.activationCalls)
        assertNotNull((coordinator.state.value as LicenseGateUiState.ActivationRequired).validationMessage)
    }

    @Test fun backendFailuresMapToDomainStates() = runTest(dispatcher) {
        assertActivationMaps(LicenseErrorCode.LICENSE_INVALID, LicenseState.Unactivated::class.java)
        assertActivationMaps(LicenseErrorCode.LICENSE_REVOKED, LicenseState.Revoked::class.java)
        assertActivationMaps(LicenseErrorCode.LICENSE_EXPIRED, LicenseState.Expired::class.java)
        assertActivationMaps(LicenseErrorCode.DEVICE_MISMATCH, LicenseState.DeviceMismatch::class.java)
        assertActivationMaps(LicenseErrorCode.DEVICE_LIMIT_REACHED, LicenseState.Error::class.java)
    }

    @Test fun networkWithoutStoredTokenRequiresNetwork() = runTest(dispatcher) {
        val coordinator = coordinator(FakeRepository(activation = failure(LicenseFailure.NetworkUnavailable)))
        runCurrent(); coordinator.updateLicenseKey("APP-AAAA-BBBB-CCCC"); coordinator.activate(); runCurrent()
        assertState<LicenseState.NetworkRequired>(coordinator)
    }

    @Test fun networkFailurePreservesNewlyAvailableValidStoredToken() = runTest(dispatcher) {
        val repo = FakeRepository(activation = failure(LicenseFailure.NetworkUnavailable))
        val coordinator = coordinator(repo); runCurrent()
        coordinator.updateLicenseKey("APP-AAAA-BBBB-CCCC")
        repo.token = "old-token"; repo.verifiedState = LicenseState.Active(claims())
        coordinator.activate(); runCurrent()
        assertTrue((coordinator.state.value as LicenseGateUiState.Allowed).licenseState is LicenseState.OfflineGrace)
    }

    @Test fun httpSuccessWithInvalidTokenDoesNotOpenGate() = runTest(dispatcher) {
        val coordinator = coordinator(FakeRepository(activation = failure(LicenseFailure.Rejected(LicenseErrorCode.TOKEN_INVALID))))
        runCurrent(); coordinator.updateLicenseKey("APP-AAAA-BBBB-CCCC"); coordinator.activate(); runCurrent()
        assertFalse(coordinator.state.value is LicenseGateUiState.Allowed)
    }

    @Test fun restartReverifiesTokenAndDoesNotPersistActiveState() = runTest(dispatcher) {
        val repo = FakeRepository("token", LicenseState.Active(claims()))
        val first = coordinator(repo); runCurrent()
        val restarted = coordinator(repo); runCurrent()
        assertTrue((first.state.value as LicenseGateUiState.Allowed).licenseState is LicenseState.OfflineGrace)
        assertTrue((restarted.state.value as LicenseGateUiState.Allowed).licenseState is LicenseState.OfflineGrace)
        assertEquals(2, repo.verifyCalls)
    }

    @Test fun startupAllowsLocalTokenAndRefreshSuccessBecomesActive() = runTest(dispatcher) {
        val refreshed = claims(expires = now + 1_000)
        val repo = FakeRepository("token", LicenseState.Active(claims()),
            refresh = LicenseRefreshResult.Success(LicenseState.Active(refreshed)))
        val coordinator = coordinator(repo)
        runCurrent()
        assertTrue((coordinator.state.value as LicenseGateUiState.Allowed).licenseState is LicenseState.Active)
        assertEquals(1, repo.refreshCalls)
    }

    @Test fun transientRefreshKeepsOfflineGraceButAuthoritativeRevokeBlocks() = runTest(dispatcher) {
        val transient = coordinator(FakeRepository("token", LicenseState.Active(claims()),
            refresh = LicenseRefreshResult.TransientFailure))
        runCurrent()
        assertTrue((transient.state.value as LicenseGateUiState.Allowed).licenseState is LicenseState.OfflineGrace)
        val revoked = coordinator(FakeRepository("token", LicenseState.Active(claims()),
            refresh = LicenseRefreshResult.AuthoritativeFailure(LicenseFailure.Rejected(LicenseErrorCode.LICENSE_REVOKED))))
        runCurrent()
        assertState<LicenseState.Revoked>(revoked)
    }

    @Test fun activeAndOfflineAllowMainAppWhileAllOthersBlock() {
        assertTrue(LicenseState.Active(claims()).allowsMainApplication())
        assertTrue(LicenseState.OfflineGrace(claims()).allowsMainApplication())
        listOf(LicenseState.Unactivated, LicenseState.Activating, LicenseState.Expired(null),
            LicenseState.DeviceMismatch(null), LicenseState.DeviceRevoked(null), LicenseState.Revoked(null), LicenseState.NetworkRequired(null),
            LicenseState.Error(LicenseFailure.Unexpected)).forEach { assertFalse(it.allowsMainApplication()) }
    }

    private suspend fun TestScope.assertActivationMaps(code: LicenseErrorCode, expected: Class<out LicenseState>) {
        val coordinator = coordinator(FakeRepository(activation = failure(LicenseFailure.Rejected(code))))
        runCurrent(); coordinator.updateLicenseKey("APP-AAAA-BBBB-CCCC"); coordinator.activate(); runCurrent()
        val actual = (coordinator.state.value as LicenseGateUiState.ActivationRequired).licenseState
        assertTrue("Expected ${expected.simpleName}, got ${actual::class.java.simpleName}", expected.isInstance(actual))
    }

    private inline fun <reified T : LicenseState> assertState(coordinator: LicenseRuntimeCoordinator) {
        val state = coordinator.state.value as LicenseGateUiState.ActivationRequired
        assertTrue(state.licenseState is T)
    }
    private fun coordinator(repository: FakeRepository) = LicenseRuntimeCoordinator(repository, LicenseTimeProvider { now * 1_000 })
    private fun claims(expires: Long = now + 100) = LicenseTokenClaims("license", "installation", now - 1, expires, expires, "com.example", 1)
    private fun failure(value: LicenseFailure) = LicenseActivationResult.Failure(value, "request")

    private class FakeRepository(
        var token: String? = null,
        var verifiedState: LicenseState = LicenseState.Unactivated,
        var activation: LicenseActivationResult = LicenseActivationResult.Failure(LicenseFailure.Unexpected, null),
        var refresh: LicenseRefreshResult = LicenseRefreshResult.NotDue,
    ) : LicenseRepository {
        var activationCalls = 0
        var verifyCalls = 0
        var refreshCalls = 0
        override suspend fun activate(licenseKey: LicenseKey): LicenseActivationResult { activationCalls++; return activation }
        override suspend fun loadStoredToken() = token
        override suspend fun verifyStoredToken(): LicenseState { verifyCalls++; return verifiedState }
        override suspend fun clearInvalidToken() { token = null }
        override suspend fun refreshIfDue(claims: LicenseTokenClaims) = refresh.also { refreshCalls++ }
    }
}
