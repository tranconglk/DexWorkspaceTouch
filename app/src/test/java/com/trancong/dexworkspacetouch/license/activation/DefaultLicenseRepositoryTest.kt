package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.*
import com.trancong.dexworkspacetouch.license.identity.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class DefaultLicenseRepositoryTest {
    private val device = LicenseDeviceIdentity("iid", "a".repeat(64), "public")
    private val app = LicensedApplicationIdentity("com.example.app", "1", 1)
    private val claims = LicenseTokenClaims("lid", "iid", 1, 10, 10, app.packageName, 1)
    private val challengeBytes = ByteArray(32) { it.toByte() }
    private val validChallenge = LicenseChallengeApiResult.Success("cid", encode(challengeBytes), 20, 1, 1, "challenge-rid")

    @Test fun completeProtocolSignsExactRawChallengeAndStoresVerifiedToken() = runBlocking {
        val fixture = fixture()
        assertTrue(fixture.repository.activate(LicenseKey.parse("APP-AAAA-BBBB-CCCC")) is LicenseActivationResult.Success)
        assertArrayEquals(challengeBytes, fixture.keyManager.signedPayload)
        assertEquals("signed", fixture.store.value)
        assertEquals("cid", fixture.api.proof?.challengeId)
        assertEquals(encode(fixture.keyManager.signature), fixture.api.proof?.signatureBase64UrlDer)
    }
    @Test fun malformedOrWrongSizedChallengeDoesNotSign() = runBlocking {
        for (challenge in listOf("%%%", encode(ByteArray(31)), encode(ByteArray(33)))) {
            val fixture = fixture(challengeResult = validChallenge.copy(challengeBase64Url = challenge))
            assertTrue(fixture.repository.activate(LicenseKey.parse("APP-AAAA-BBBB-CCCC")) is LicenseActivationResult.Failure)
            assertNull(fixture.keyManager.signedPayload); assertNull(fixture.api.proof)
        }
    }
    @Test fun proofFailureAndInvalidTokenNeverReplaceOldToken() = runBlocking {
        val rejected = fixture(oldToken = "old", proofResult = LicenseApiResult.Rejected(LicenseErrorCode.PROOF_INVALID, "r"))
        rejected.repository.activate(LicenseKey.parse("APP-AAAA-BBBB-CCCC")); assertEquals("old", rejected.store.value)
        val invalid = fixture(oldToken = "old", validation = LicenseTokenVerificationResult.Invalid(LicenseTokenInvalidReason.INVALID_SIGNATURE))
        invalid.repository.activate(LicenseKey.parse("APP-AAAA-BBBB-CCCC")); assertEquals("old", invalid.store.value)
    }
    @Test fun networkFailureDoesNotEraseOldToken() = runBlocking {
        val fixture = fixture(oldToken = "old", challengeResult = LicenseChallengeApiResult.NetworkUnavailable)
        fixture.repository.activate(LicenseKey.parse("APP-AAAA-BBBB-CCCC")); assertEquals("old", fixture.store.value)
    }
    @Test fun canonicalKeyIsSentButNeverStored() = runBlocking {
        val fixture = fixture(); fixture.repository.activate(LicenseKey.parse("app-aaaa-bbbb-cccc"))
        assertEquals("APP-AAAA-BBBB-CCCC", fixture.api.command?.licenseKey?.value)
        assertFalse(fixture.store.writes.any { it.contains("APP-AAAA-BBBB-CCCC") })
    }
    @Test fun invalidStoredTokenIsCleared() = runBlocking {
        val fixture = fixture(oldToken = "bad", validation = LicenseTokenVerificationResult.Invalid(LicenseTokenInvalidReason.TOKEN_EXPIRED))
        assertTrue(fixture.repository.verifyStoredToken() is LicenseState.Error); assertNull(fixture.store.value)
    }
    @Test fun dueRefreshSignsExactChallengeAndAtomicallyReplacesVerifiedToken() = runBlocking {
        val fixture = fixture(oldToken = "old")
        val result = fixture.repository.refreshIfDue(claims)
        assertTrue(result is LicenseRefreshResult.Success)
        assertArrayEquals(challengeBytes, fixture.keyManager.signedPayload)
        assertEquals("signed", fixture.store.value)
        assertEquals("old", fixture.api.refreshBearer)
    }
    @Test fun refreshNetworkFailureAndInvalidNewTokenRetainOldToken() = runBlocking {
        val network = fixture(oldToken = "old", refreshChallenge = LicenseChallengeApiResult.NetworkUnavailable)
        assertEquals(LicenseRefreshResult.TransientFailure, network.repository.refreshIfDue(claims))
        assertEquals("old", network.store.value)
        val invalid = fixture(oldToken = "old", validation = LicenseTokenVerificationResult.Invalid(LicenseTokenInvalidReason.INVALID_SIGNATURE))
        assertEquals(LicenseRefreshResult.IntegrityFailure, invalid.repository.refreshIfDue(claims))
        assertEquals("old", invalid.store.value)
    }
    @Test fun authoritativeRefreshRejectionClearsOldToken() = runBlocking {
        val fixture = fixture(oldToken = "old", refreshChallenge =
            LicenseChallengeApiResult.Rejected(LicenseErrorCode.LICENSE_REVOKED, "r"))
        assertTrue(fixture.repository.refreshIfDue(claims) is LicenseRefreshResult.AuthoritativeFailure)
        assertNull(fixture.store.value)
    }
    @Test fun refreshRateLimitIsTransientAndRetainsOldToken() = runBlocking {
        val fixture = fixture(oldToken = "old", refreshChallenge =
            LicenseChallengeApiResult.Rejected(LicenseErrorCode.RATE_LIMITED, "r"))
        assertEquals(LicenseRefreshResult.TransientFailure, fixture.repository.refreshIfDue(claims))
        assertEquals("old", fixture.store.value)
    }
    @Test fun refreshNotDueMakesNoApiCall() = runBlocking {
        val notDue = claims.copy(issuedAtEpochSeconds = 2, expiresAtEpochSeconds = 500_000,
            offlineValidUntilEpochSeconds = 500_000)
        val fixture = fixture(oldToken = "old")
        assertEquals(LicenseRefreshResult.NotDue, fixture.repository.refreshIfDue(notDue))
        assertNull(fixture.api.refreshBearer)
    }

    private fun fixture(oldToken: String? = null, challengeResult: LicenseChallengeApiResult = validChallenge,
        proofResult: LicenseApiResult = LicenseApiResult.Success("signed", "proof-rid"),
        validation: LicenseTokenVerificationResult = LicenseTokenVerificationResult.Valid(VerifiedLicenseToken("signed", claims, "did", device.deviceHash)),
        refreshChallenge: LicenseChallengeApiResult = validChallenge,
        refreshResult: LicenseApiResult = proofResult): Fixture {
        val keyManager = FakeKeyManager(); val api = FakeApi(challengeResult, proofResult, refreshChallenge, refreshResult); val store = FakeStore(oldToken)
        return Fixture(DefaultLicenseRepository(DeviceIdentityProvider { device }, keyManager,
            ApplicationIdentityProvider { app }, api, LicenseTokenValidation { _, _, _ -> validation }, store,
            LicenseTimeProvider { 2_000L }), keyManager, api, store)
    }
    private data class Fixture(val repository: DefaultLicenseRepository, val keyManager: FakeKeyManager, val api: FakeApi, val store: FakeStore)
    private class FakeKeyManager : DeviceKeyManager {
        val signature = byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x01); var signedPayload: ByteArray? = null
        override suspend fun ensureKeyExists() = Unit
        override suspend fun getPublicKeyBase64() = "public"
        override suspend fun sign(payload: ByteArray): ByteArray { signedPayload = payload.copyOf(); return signature }
    }
    private class FakeApi(val challenge: LicenseChallengeApiResult, val result: LicenseApiResult,
        val refreshChallenge: LicenseChallengeApiResult, val refreshResult: LicenseApiResult) : LicenseApiClient {
        var command: LicenseActivationCommand? = null; var proof: LicenseActivationProof? = null
        var refreshBearer: String? = null
        override suspend fun requestChallenge(command: LicenseActivationCommand) = challenge.also { this.command = command }
        override suspend fun activate(proof: LicenseActivationProof) = result.also { this.proof = proof }
        override suspend fun requestRefreshChallenge(token: String, application: LicensedApplicationIdentity) =
            refreshChallenge.also { refreshBearer = token }
        override suspend fun refresh(proof: LicenseActivationProof) = refreshResult
    }
    private class FakeStore(var value: String? = null) : LicenseTokenStore {
        val writes = mutableListOf<String>()
        override suspend fun load() = value
        override suspend fun saveVerified(token: String) { writes += token; value = token }
        override suspend fun clear() { value = null }
    }
    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
