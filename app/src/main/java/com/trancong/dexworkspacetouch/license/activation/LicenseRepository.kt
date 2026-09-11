package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.LicenseActivationCommand
import com.trancong.dexworkspacetouch.license.domain.LicenseErrorCode
import com.trancong.dexworkspacetouch.license.domain.LicenseFailure
import com.trancong.dexworkspacetouch.license.domain.LicenseKey
import com.trancong.dexworkspacetouch.license.domain.LicenseState
import com.trancong.dexworkspacetouch.license.identity.DeviceIdentityProvider
import com.trancong.dexworkspacetouch.license.identity.DeviceKeyManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Base64

interface LicenseRepository {
    suspend fun activate(licenseKey: LicenseKey): LicenseActivationResult
    suspend fun loadStoredToken(): String?
    suspend fun verifyStoredToken(): LicenseState
    suspend fun clearInvalidToken()
    suspend fun refreshIfDue(claims: com.trancong.dexworkspacetouch.license.domain.LicenseTokenClaims): LicenseRefreshResult
}

sealed interface LicenseRefreshResult {
    data object NotDue : LicenseRefreshResult
    data class Success(val state: LicenseState.Active) : LicenseRefreshResult
    data object TransientFailure : LicenseRefreshResult
    data class AuthoritativeFailure(val failure: LicenseFailure) : LicenseRefreshResult
    data object IntegrityFailure : LicenseRefreshResult
}

class DefaultLicenseRepository(
    private val deviceIdentityProvider: DeviceIdentityProvider,
    private val deviceKeyManager: DeviceKeyManager,
    private val applicationIdentityProvider: ApplicationIdentityProvider,
    private val apiClient: LicenseApiClient,
    private val tokenVerifier: LicenseTokenValidation,
    private val tokenStore: LicenseTokenStore,
    private val timeProvider: com.trancong.dexworkspacetouch.license.domain.LicenseTimeProvider,
) : LicenseRepository {
    private val activationMutex = Mutex()
    private val refreshMutex = Mutex()

    override suspend fun activate(licenseKey: LicenseKey): LicenseActivationResult = activationMutex.withLock {
        val device = deviceIdentityProvider.get()
        val application = applicationIdentityProvider.get()
        val challenge = apiClient.requestChallenge(LicenseActivationCommand(licenseKey, device, application))
        if (challenge !is LicenseChallengeApiResult.Success) return@withLock challenge.toActivationFailure()
        val challengeBytes = decodeChallenge(challenge.challengeBase64Url)
            ?: return@withLock LicenseActivationResult.Failure(LicenseFailure.InvalidResponse, challenge.requestId)
        val signature = deviceKeyManager.sign(challengeBytes)
        val proof = LicenseActivationProof(
            challengeId = challenge.challengeId,
            signatureBase64UrlDer = Base64.getUrlEncoder().withoutPadding().encodeToString(signature),
        )
        when (val response = apiClient.activate(proof)) {
            is LicenseApiResult.Success -> when (val verification = tokenVerifier.verify(response.licenseToken, device, application)) {
                is LicenseTokenVerificationResult.Valid -> {
                    tokenStore.saveVerified(response.licenseToken)
                    LicenseActivationResult.Success(LicenseState.Active(verification.token.claims), response.requestId)
                }
                is LicenseTokenVerificationResult.Invalid -> LicenseActivationResult.Failure(
                    verification.reason.toFailure(), response.requestId,
                )
            }
            is LicenseApiResult.Rejected -> LicenseActivationResult.Failure(LicenseFailure.Rejected(response.code), response.requestId)
            is LicenseApiResult.ServerError -> LicenseActivationResult.Failure(LicenseFailure.Server(response.retryable), response.requestId)
            is LicenseApiResult.InvalidResponse -> LicenseActivationResult.Failure(LicenseFailure.InvalidResponse, response.requestId)
            LicenseApiResult.NetworkUnavailable -> LicenseActivationResult.Failure(LicenseFailure.NetworkUnavailable, null)
        }
    }

    override suspend fun loadStoredToken(): String? = tokenStore.load()

    override suspend fun verifyStoredToken(): LicenseState {
        val encoded = tokenStore.load() ?: return LicenseState.Unactivated
        val result = tokenVerifier.verify(encoded, deviceIdentityProvider.get(), applicationIdentityProvider.get())
        return when (result) {
            is LicenseTokenVerificationResult.Valid -> LicenseState.Active(result.token.claims)
            is LicenseTokenVerificationResult.Invalid -> {
                tokenStore.clear()
                LicenseState.Error(result.reason.toFailure())
            }
        }
    }

    override suspend fun clearInvalidToken() = tokenStore.clear()

    override suspend fun refreshIfDue(
        claims: com.trancong.dexworkspacetouch.license.domain.LicenseTokenClaims,
    ): LicenseRefreshResult = refreshMutex.withLock {
        val now = timeProvider.nowEpochMillis() / 1_000L
        val effectiveEnd = minOf(claims.expiresAtEpochSeconds, claims.offlineValidUntilEpochSeconds)
        if (now < claims.issuedAtEpochSeconds + REFRESH_INTERVAL_SECONDS &&
            effectiveEnd - now > REFRESH_EARLY_WINDOW_SECONDS) return@withLock LicenseRefreshResult.NotDue
        val oldToken = tokenStore.load() ?: return@withLock LicenseRefreshResult.IntegrityFailure
        val device = deviceIdentityProvider.get()
        val application = applicationIdentityProvider.get()
        val challenge = apiClient.requestRefreshChallenge(oldToken, application)
        if (challenge !is LicenseChallengeApiResult.Success) return@withLock classify(challenge)
        val bytes = decodeChallenge(challenge.challengeBase64Url) ?: return@withLock LicenseRefreshResult.IntegrityFailure
        val proof = LicenseActivationProof(challenge.challengeId,
            Base64.getUrlEncoder().withoutPadding().encodeToString(deviceKeyManager.sign(bytes)))
        when (val response = apiClient.refresh(proof)) {
            is LicenseApiResult.Success -> when (val verification = tokenVerifier.verify(response.licenseToken, device, application)) {
                is LicenseTokenVerificationResult.Valid -> {
                    tokenStore.saveVerified(response.licenseToken)
                    LicenseRefreshResult.Success(LicenseState.Active(verification.token.claims))
                }
                is LicenseTokenVerificationResult.Invalid -> LicenseRefreshResult.IntegrityFailure
            }
            is LicenseApiResult.Rejected -> authoritativeOrIntegrity(response.code)
            is LicenseApiResult.ServerError, LicenseApiResult.NetworkUnavailable -> LicenseRefreshResult.TransientFailure
            is LicenseApiResult.InvalidResponse -> LicenseRefreshResult.IntegrityFailure
        }
    }

    private suspend fun classify(result: LicenseChallengeApiResult): LicenseRefreshResult = when (result) {
        is LicenseChallengeApiResult.Rejected -> authoritativeOrIntegrity(result.code)
        is LicenseChallengeApiResult.ServerError, LicenseChallengeApiResult.NetworkUnavailable -> LicenseRefreshResult.TransientFailure
        is LicenseChallengeApiResult.InvalidResponse -> LicenseRefreshResult.IntegrityFailure
        is LicenseChallengeApiResult.Success -> error("Success is not a failure")
    }

    private suspend fun authoritativeOrIntegrity(code: LicenseErrorCode): LicenseRefreshResult {
        if (code == LicenseErrorCode.RATE_LIMITED) return LicenseRefreshResult.TransientFailure
        if (code !in AUTHORITATIVE_REFRESH_FAILURES) return LicenseRefreshResult.IntegrityFailure
        tokenStore.clear()
        return LicenseRefreshResult.AuthoritativeFailure(LicenseFailure.Rejected(code))
    }

    private fun decodeChallenge(value: String): ByteArray? {
        if (value.isEmpty() || value.length > 64 || !BASE64URL.matches(value) || '=' in value) return null
        return try { Base64.getUrlDecoder().decode(value).takeIf { it.size == CHALLENGE_BYTES } }
        catch (_: IllegalArgumentException) { null }
    }

    private fun LicenseChallengeApiResult.toActivationFailure(): LicenseActivationResult.Failure = when (this) {
        is LicenseChallengeApiResult.Rejected -> LicenseActivationResult.Failure(LicenseFailure.Rejected(code), requestId)
        is LicenseChallengeApiResult.ServerError -> LicenseActivationResult.Failure(LicenseFailure.Server(retryable), requestId)
        is LicenseChallengeApiResult.InvalidResponse -> LicenseActivationResult.Failure(LicenseFailure.InvalidResponse, requestId)
        LicenseChallengeApiResult.NetworkUnavailable -> LicenseActivationResult.Failure(LicenseFailure.NetworkUnavailable, null)
        is LicenseChallengeApiResult.Success -> error("Success is not a failure")
    }

    private fun LicenseTokenInvalidReason.toFailure(): LicenseFailure = when (this) {
        LicenseTokenInvalidReason.TOKEN_EXPIRED -> LicenseFailure.Rejected(LicenseErrorCode.TOKEN_EXPIRED)
        LicenseTokenInvalidReason.DEVICE_MISMATCH,
        LicenseTokenInvalidReason.APPLICATION_MISMATCH -> LicenseFailure.Rejected(LicenseErrorCode.DEVICE_MISMATCH)
        LicenseTokenInvalidReason.MALFORMED,
        LicenseTokenInvalidReason.UNKNOWN_KEY,
        LicenseTokenInvalidReason.INVALID_SIGNATURE,
        LicenseTokenInvalidReason.INVALID_CLAIMS -> LicenseFailure.LocalDataCorrupted
        LicenseTokenInvalidReason.UNSUPPORTED_HEADER -> LicenseFailure.Rejected(LicenseErrorCode.TOKEN_INVALID)
    }

    private companion object {
        const val CHALLENGE_BYTES = 32
        val BASE64URL = Regex("[A-Za-z0-9_-]+")
        const val REFRESH_INTERVAL_SECONDS = 24 * 60 * 60L
        const val REFRESH_EARLY_WINDOW_SECONDS = 72 * 60 * 60L
        val AUTHORITATIVE_REFRESH_FAILURES = setOf(
            LicenseErrorCode.LICENSE_REVOKED,
            LicenseErrorCode.LICENSE_EXPIRED,
            LicenseErrorCode.DEVICE_REVOKED,
            LicenseErrorCode.DEVICE_MISMATCH,
            LicenseErrorCode.APPLICATION_NOT_ALLOWED,
        )
    }
}
