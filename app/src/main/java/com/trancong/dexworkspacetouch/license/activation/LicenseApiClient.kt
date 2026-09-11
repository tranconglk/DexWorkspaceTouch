package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.LicenseActivationCommand
import com.trancong.dexworkspacetouch.license.domain.LicenseErrorCode
import com.trancong.dexworkspacetouch.license.domain.LicensedApplicationIdentity

interface LicenseApiClient {
    suspend fun requestChallenge(command: LicenseActivationCommand): LicenseChallengeApiResult
    suspend fun activate(proof: LicenseActivationProof): LicenseApiResult
    suspend fun requestRefreshChallenge(token: String, application: LicensedApplicationIdentity): LicenseChallengeApiResult
    suspend fun refresh(proof: LicenseActivationProof): LicenseApiResult
}

data class LicenseActivationProof(val challengeId: String, val signatureBase64UrlDer: String)

sealed interface LicenseChallengeApiResult {
    data class Success(
        val challengeId: String,
        val challengeBase64Url: String,
        val expiresAtEpochSeconds: Long,
        val serverTimeEpochSeconds: Long,
        val proofVersion: Int,
        val requestId: String?,
    ) : LicenseChallengeApiResult
    data class Rejected(val code: LicenseErrorCode, val requestId: String?) : LicenseChallengeApiResult
    data class ServerError(val retryable: Boolean, val requestId: String?) : LicenseChallengeApiResult
    data object NetworkUnavailable : LicenseChallengeApiResult
    data class InvalidResponse(val requestId: String?) : LicenseChallengeApiResult
}

sealed interface LicenseApiResult {
    data class Success(val licenseToken: String, val requestId: String?) : LicenseApiResult
    data class Rejected(val code: LicenseErrorCode, val requestId: String?) : LicenseApiResult
    data class ServerError(val retryable: Boolean, val requestId: String?) : LicenseApiResult
    data object NetworkUnavailable : LicenseApiResult
    data class InvalidResponse(val requestId: String?) : LicenseApiResult
}
