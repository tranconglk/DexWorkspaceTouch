package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.LicenseDeviceIdentity
import com.trancong.dexworkspacetouch.license.domain.LicensePolicy
import com.trancong.dexworkspacetouch.license.domain.LicenseTimeProvider
import com.trancong.dexworkspacetouch.license.domain.LicenseTokenClaims
import com.trancong.dexworkspacetouch.license.domain.LicensedApplicationIdentity
import org.json.JSONObject
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.RSAPublicKey
import java.util.Base64

class TrustedLicenseSigningKeys private constructor(private val keys: Map<String, PublicKey>) {
    operator fun get(kid: String): PublicKey? = keys[kid]
    val size: Int get() = keys.size

    companion object {
        private val KID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")

        fun emptyForDebug(): TrustedLicenseSigningKeys = TrustedLicenseSigningKeys(emptyMap())

        fun fromRegistryJson(json: String): TrustedLicenseSigningKeys {
            require(json.isNotBlank()) { "Trusted signing-key registry is empty." }
            val entries = org.json.JSONArray(json)
            require(entries.length() > 0) { "Trusted signing-key registry is empty." }
            val configured = LinkedHashMap<String, String>()
            repeat(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                require(entry.length() == 3 && entry.has("kid") && entry.has("algorithm") && entry.has("spkiBase64")) {
                    "Trusted signing-key entry has unsupported fields."
                }
                val kid = entry.getString("kid")
                require(KID_PATTERN.matches(kid)) { "Trusted signing-key ID is invalid." }
                require(entry.getString("algorithm") == "RS256") { "Trusted signing-key algorithm is unsupported." }
                require(configured.put(kid, entry.getString("spkiBase64")) == null) { "Duplicate trusted signing-key ID." }
            }
            return fromBase64Spki(configured)
        }

        fun fromBase64Spki(keys: Map<String, String>): TrustedLicenseSigningKeys {
            require(keys.isNotEmpty()) { "Trusted signing-key registry is empty." }
            val factory = KeyFactory.getInstance("RSA")
            val parsed = keys.mapValues { (kid, value) ->
                require(KID_PATTERN.matches(kid)) { "Trusted signing-key ID is invalid." }
                val decoded = Base64.getDecoder().decode(value)
                val key = factory.generatePublic(X509EncodedKeySpec(decoded)) as? RSAPublicKey
                    ?: error("Trusted signing key is not RSA.")
                require(key.modulus.bitLength() >= 2048) { "Trusted RSA signing key is smaller than 2048 bits." }
                key
            }
            return TrustedLicenseSigningKeys(parsed)
        }
    }
}

fun interface LicenseTokenValidation {
    fun verify(
        encoded: String,
        device: LicenseDeviceIdentity,
        application: LicensedApplicationIdentity,
    ): LicenseTokenVerificationResult
}

class LicenseTokenVerifier(
    private val trustedKeys: TrustedLicenseSigningKeys,
    private val timeProvider: LicenseTimeProvider,
) : LicenseTokenValidation {
    override fun verify(
        encoded: String,
        device: LicenseDeviceIdentity,
        application: LicensedApplicationIdentity,
    ): LicenseTokenVerificationResult {
        if (encoded.length !in 1..MAX_TOKEN_CHARS) return invalid(LicenseTokenInvalidReason.MALFORMED)
        val segments = encoded.split('.')
        if (segments.size != 3 || segments.any(String::isEmpty)) return invalid(LicenseTokenInvalidReason.MALFORMED)
        if (segments[0].length > MAX_HEADER_CHARS || segments[1].length > MAX_PAYLOAD_CHARS ||
            segments[2].length > MAX_SIGNATURE_CHARS || segments.any { '=' in it }) {
            return invalid(LicenseTokenInvalidReason.MALFORMED)
        }
        val decoder = Base64.getUrlDecoder()
        val header = try { JSONObject(String(decoder.decode(segments[0]), Charsets.UTF_8)) }
        catch (_: IllegalArgumentException) { return invalid(LicenseTokenInvalidReason.MALFORMED) }
        catch (_: org.json.JSONException) { return invalid(LicenseTokenInvalidReason.MALFORMED) }
        if (header.optString("alg") != "RS256" || header.optString("typ") != "DWT-LICENSE") {
            return invalid(LicenseTokenInvalidReason.UNSUPPORTED_HEADER)
        }
        val kid = header.optString("kid")
        val key = trustedKeys[kid] ?: return invalid(LicenseTokenInvalidReason.UNKNOWN_KEY)
        val signatureBytes = try { decoder.decode(segments[2]) }
        catch (_: IllegalArgumentException) { return invalid(LicenseTokenInvalidReason.MALFORMED) }
        val signatureValid = try {
            Signature.getInstance("SHA256withRSA").run {
                initVerify(key)
                update("${segments[0]}.${segments[1]}".toByteArray(Charsets.US_ASCII))
                verify(signatureBytes)
            }
        } catch (_: java.security.GeneralSecurityException) { false }
        if (!signatureValid) return invalid(LicenseTokenInvalidReason.INVALID_SIGNATURE)

        val payload = try { JSONObject(String(decoder.decode(segments[1]), Charsets.UTF_8)) }
        catch (_: IllegalArgumentException) { return invalid(LicenseTokenInvalidReason.MALFORMED) }
        catch (_: org.json.JSONException) { return invalid(LicenseTokenInvalidReason.MALFORMED) }
        val verified = try {
            val claims = LicenseTokenClaims(
                licenseId = payload.requireString("licenseId"),
                installationId = payload.requireString("installationId"),
                issuedAtEpochSeconds = payload.requireLong("issuedAtEpochSeconds"),
                expiresAtEpochSeconds = payload.requireLong("expiresAtEpochSeconds"),
                offlineValidUntilEpochSeconds = payload.requireLong("offlineValidUntilEpochSeconds"),
                packageName = payload.requireString("packageName"),
                tokenVersion = payload.requireInt("tokenVersion"),
            )
            VerifiedLicenseToken(encoded, claims, payload.requireString("deviceId"), payload.requireString("deviceHash"))
        } catch (_: Exception) { return invalid(LicenseTokenInvalidReason.INVALID_CLAIMS) }
        val claims = verified.claims
        if (claims.tokenVersion != LicensePolicy.TOKEN_VERSION || claims.issuedAtEpochSeconds > timeProvider.nowEpochMillis() / 1_000L) {
            return invalid(LicenseTokenInvalidReason.INVALID_CLAIMS)
        }
        if (timeProvider.nowEpochMillis() / 1_000L >= claims.expiresAtEpochSeconds) return invalid(LicenseTokenInvalidReason.TOKEN_EXPIRED)
        if (claims.installationId != device.installationId || verified.deviceHash != device.deviceHash) {
            return invalid(LicenseTokenInvalidReason.DEVICE_MISMATCH)
        }
        if (claims.packageName != application.packageName) return invalid(LicenseTokenInvalidReason.APPLICATION_MISMATCH)
        return LicenseTokenVerificationResult.Valid(verified)
    }

    private fun JSONObject.requireString(name: String): String = (get(name) as? String).orEmpty()
        .also { require(it.isNotBlank()) }
    private fun JSONObject.requireLong(name: String): Long {
        val value = get(name) as? Number ?: error("$name must be numeric")
        val result = value.toLong()
        require(value.toDouble() == result.toDouble()) { "$name must be an integer" }
        return result
    }
    private fun JSONObject.requireInt(name: String): Int {
        val value = requireLong(name)
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "$name is out of range" }
        return value.toInt()
    }
    private fun invalid(reason: LicenseTokenInvalidReason) = LicenseTokenVerificationResult.Invalid(reason)

    private companion object {
        const val MAX_TOKEN_CHARS = 16_384
        const val MAX_HEADER_CHARS = 2_048
        const val MAX_PAYLOAD_CHARS = 8_192
        const val MAX_SIGNATURE_CHARS = 4_096
    }
}
