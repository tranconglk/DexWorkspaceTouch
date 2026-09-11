package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.LicenseActivationCommand
import com.trancong.dexworkspacetouch.license.domain.LicenseErrorCode
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class OkHttpLicenseApiClient(
    baseUrl: String,
    allowCleartext: Boolean,
    private val client: OkHttpClient = defaultClient(),
) : LicenseApiClient {
    private val normalizedBaseUrl = validateBaseUrl(baseUrl, allowCleartext)

    override suspend fun requestChallenge(command: LicenseActivationCommand): LicenseChallengeApiResult {
        val application = JSONObject().put("packageName", command.application.packageName)
            .put("versionName", command.application.versionName).put("versionCode", command.application.versionCode)
        command.application.signingCertificateSha256?.let { application.put("signingCertificateSha256", it) }
        val json = JSONObject().put("licenseKey", command.licenseKey.value)
            .put("device", JSONObject().put("installationId", command.device.installationId)
                .put("deviceHash", command.device.deviceHash).put("publicKey", command.device.publicKey))
            .put("application", application)
        return when (val response = execute("v1/license/challenge", json)) {
            is HttpJsonResult.Success -> {
                val data = response.json.optJSONObject("data")
                val challengeId = data?.optString("challengeId").orEmpty()
                val challenge = data?.optString("challenge").orEmpty()
                val proofVersion = data?.optInt("proofVersion", -1) ?: -1
                if (!response.json.optBoolean("ok") || challengeId.isBlank() || challenge.isBlank() ||
                    proofVersion != 1 || data == null || !data.has("expiresAt") || !data.has("serverTime")) {
                    LicenseChallengeApiResult.InvalidResponse(response.requestId)
                } else LicenseChallengeApiResult.Success(challengeId, challenge, data.getLong("expiresAt"),
                    data.getLong("serverTime"), proofVersion, response.requestId)
            }
            is HttpJsonResult.Rejected -> LicenseChallengeApiResult.Rejected(response.code, response.requestId)
            is HttpJsonResult.ServerError -> LicenseChallengeApiResult.ServerError(true, response.requestId)
            is HttpJsonResult.Invalid -> LicenseChallengeApiResult.InvalidResponse(response.requestId)
            HttpJsonResult.Network -> LicenseChallengeApiResult.NetworkUnavailable
        }
    }

    override suspend fun activate(proof: LicenseActivationProof): LicenseApiResult {
        val json = JSONObject().put("proofVersion", 1).put("challengeId", proof.challengeId)
            .put("signature", proof.signatureBase64UrlDer)
        return when (val response = execute("v1/license/activate", json)) {
            is HttpJsonResult.Success -> {
                val token = response.json.optJSONObject("data")?.optString("licenseToken").orEmpty()
                if (response.json.optBoolean("ok") && token.isNotBlank()) LicenseApiResult.Success(token, response.requestId)
                else LicenseApiResult.InvalidResponse(response.requestId)
            }
            is HttpJsonResult.Rejected -> LicenseApiResult.Rejected(response.code, response.requestId)
            is HttpJsonResult.ServerError -> LicenseApiResult.ServerError(true, response.requestId)
            is HttpJsonResult.Invalid -> LicenseApiResult.InvalidResponse(response.requestId)
            HttpJsonResult.Network -> LicenseApiResult.NetworkUnavailable
        }
    }

    override suspend fun requestRefreshChallenge(
        token: String,
        application: com.trancong.dexworkspacetouch.license.domain.LicensedApplicationIdentity,
    ): LicenseChallengeApiResult {
        val certificate = application.signingCertificateSha256
            ?: return LicenseChallengeApiResult.InvalidResponse(null)
        val json = JSONObject().put("packageName", application.packageName)
            .put("signingCertificateSha256", certificate)
        return parseChallenge(execute("v1/license/refresh/challenge", json, token))
    }

    override suspend fun refresh(proof: LicenseActivationProof): LicenseApiResult {
        val json = JSONObject().put("proofVersion", 1).put("challengeId", proof.challengeId)
            .put("signature", proof.signatureBase64UrlDer)
        return parseToken(execute("v1/license/refresh", json))
    }

    private suspend fun execute(path: String, json: JSONObject, bearerToken: String? = null): HttpJsonResult {
        val builder = Request.Builder().url(normalizedBaseUrl + path).post(json.toString().toRequestBody(JSON))
        if (bearerToken != null) builder.header("Authorization", "Bearer $bearerToken")
        val request = builder.build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(HttpJsonResult.Network)
                }
                override fun onResponse(call: Call, response: Response) = response.use {
                    val requestId = it.header("X-Request-ID")
                    if (!it.header("Content-Type").orEmpty().startsWith("application/json", true)) {
                        if (continuation.isActive) continuation.resume(HttpJsonResult.Invalid(requestId)); return
                    }
                    val body = try { it.body.string() } catch (_: IOException) {
                        if (continuation.isActive) continuation.resume(HttpJsonResult.Network); return
                    }
                    val parsed = try { JSONObject(body) } catch (_: org.json.JSONException) { null }
                    val result = when {
                        parsed == null -> HttpJsonResult.Invalid(requestId)
                        it.isSuccessful -> HttpJsonResult.Success(parsed, requestId)
                        it.code in 400..499 -> parsed.optJSONObject("error")?.optString("code")
                            ?.let(LicenseErrorCode::fromWireValue)?.let { code -> HttpJsonResult.Rejected(code, requestId) }
                            ?: HttpJsonResult.Invalid(requestId)
                        it.code >= 500 -> HttpJsonResult.ServerError(requestId)
                        else -> HttpJsonResult.Invalid(requestId)
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    private fun parseChallenge(response: HttpJsonResult): LicenseChallengeApiResult = when (response) {
        is HttpJsonResult.Success -> {
            val data = response.json.optJSONObject("data")
            val challengeId = data?.optString("challengeId").orEmpty()
            val challenge = data?.optString("challenge").orEmpty()
            val proofVersion = data?.optInt("proofVersion", -1) ?: -1
            if (!response.json.optBoolean("ok") || challengeId.isBlank() || challenge.isBlank() || proofVersion != 1 ||
                data == null || !data.has("expiresAt") || !data.has("serverTime")) LicenseChallengeApiResult.InvalidResponse(response.requestId)
            else LicenseChallengeApiResult.Success(challengeId, challenge, data.getLong("expiresAt"), data.getLong("serverTime"), proofVersion, response.requestId)
        }
        is HttpJsonResult.Rejected -> LicenseChallengeApiResult.Rejected(response.code, response.requestId)
        is HttpJsonResult.ServerError -> LicenseChallengeApiResult.ServerError(true, response.requestId)
        is HttpJsonResult.Invalid -> LicenseChallengeApiResult.InvalidResponse(response.requestId)
        HttpJsonResult.Network -> LicenseChallengeApiResult.NetworkUnavailable
    }

    private fun parseToken(response: HttpJsonResult): LicenseApiResult = when (response) {
        is HttpJsonResult.Success -> response.json.optJSONObject("data")?.optString("licenseToken").orEmpty()
            .takeIf { response.json.optBoolean("ok") && it.isNotBlank() }
            ?.let { LicenseApiResult.Success(it, response.requestId) } ?: LicenseApiResult.InvalidResponse(response.requestId)
        is HttpJsonResult.Rejected -> LicenseApiResult.Rejected(response.code, response.requestId)
        is HttpJsonResult.ServerError -> LicenseApiResult.ServerError(true, response.requestId)
        is HttpJsonResult.Invalid -> LicenseApiResult.InvalidResponse(response.requestId)
        HttpJsonResult.Network -> LicenseApiResult.NetworkUnavailable
    }

    private sealed interface HttpJsonResult {
        data class Success(val json: JSONObject, val requestId: String?) : HttpJsonResult
        data class Rejected(val code: LicenseErrorCode, val requestId: String?) : HttpJsonResult
        data class ServerError(val requestId: String?) : HttpJsonResult
        data class Invalid(val requestId: String?) : HttpJsonResult
        data object Network : HttpJsonResult
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()
        private fun validateBaseUrl(value: String, allowCleartext: Boolean): String {
            val normalized = value.trim().let { if (it.endsWith('/')) it else "$it/" }
            require(normalized.startsWith("https://") || allowCleartext && normalized.startsWith("http://")) {
                "License API base URL must use HTTPS outside debug cleartext configuration."
            }
            return normalized
        }
    }
}
