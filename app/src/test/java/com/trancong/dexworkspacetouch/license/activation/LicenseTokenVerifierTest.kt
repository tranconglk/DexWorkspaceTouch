package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.*
import java.util.Base64

class LicenseTokenVerifierTest {
    private lateinit var keyPair: KeyPair
    private lateinit var secondKeyPair: KeyPair
    private val device = LicenseDeviceIdentity("installation-1", "a".repeat(64), "public")
    private val app = LicensedApplicationIdentity("com.example.app", "1", 1)
    private val now = 1_000L
    @Before fun setUp() {
        keyPair = rsaPair()
        secondKeyPair = rsaPair()
    }

    @Test fun validRs256TokenIsAccepted() = assertTrue(verifier().verify(token(), device, app) is LicenseTokenVerificationResult.Valid)
    @Test fun tamperedPayloadIsRejectedBeforeClaimsAreTrusted() {
        val parts = token().split('.').toMutableList()
        parts[1] = encode(payload().put("licenseId", "tampered").toString().toByteArray())
        assertInvalid(parts.joinToString("."), LicenseTokenInvalidReason.INVALID_SIGNATURE)
    }
    @Test fun headerPolicyIsEnforced() {
        assertInvalid(token(header = header().put("alg", "none")), LicenseTokenInvalidReason.UNSUPPORTED_HEADER)
        assertInvalid(token(header = header().put("typ", "JWT")), LicenseTokenInvalidReason.UNSUPPORTED_HEADER)
        assertInvalid(token(header = header().put("kid", "unknown")), LicenseTokenInvalidReason.UNKNOWN_KEY)
    }
    @Test fun malformedOversizedAndBadSignatureAreRejected() {
        assertInvalid("one.two", LicenseTokenInvalidReason.MALFORMED)
        assertInvalid("x".repeat(16_385), LicenseTokenInvalidReason.MALFORMED)
        val parts = token().split('.').toMutableList().apply { this[2] = encode(ByteArray(256)) }
        assertInvalid(parts.joinToString("."), LicenseTokenInvalidReason.INVALID_SIGNATURE)
    }
    @Test fun identityPackageVersionAndTimeAreValidated() {
        assertInvalid(token(payload().put("installationId", "other")), LicenseTokenInvalidReason.DEVICE_MISMATCH)
        assertInvalid(token(payload().put("deviceHash", "b".repeat(64))), LicenseTokenInvalidReason.DEVICE_MISMATCH)
        assertInvalid(token(payload().put("packageName", "other.app")), LicenseTokenInvalidReason.APPLICATION_MISMATCH)
        assertInvalid(token(payload().put("tokenVersion", 2)), LicenseTokenInvalidReason.INVALID_CLAIMS)
        assertInvalid(token(payload().put("expiresAtEpochSeconds", now).put("offlineValidUntilEpochSeconds", now)), LicenseTokenInvalidReason.TOKEN_EXPIRED)
        assertInvalid(token(payload().put("issuedAtEpochSeconds", now + 1)), LicenseTokenInvalidReason.INVALID_CLAIMS)
    }
    @Test fun unknownClaimsAreIgnored() = assertTrue(
        verifier().verify(token(payload().put("futureClaim", true)), device, app) is LicenseTokenVerificationResult.Valid,
    )
    @Test fun missingOrWrongTypeClaimsAreRejected() {
        assertInvalid(token(payload().apply { remove("licenseId") }), LicenseTokenInvalidReason.INVALID_CLAIMS)
        assertInvalid(token(payload().put("expiresAtEpochSeconds", "1100")), LicenseTokenInvalidReason.INVALID_CLAIMS)
    }
    @Test fun multiKidRegistrySupportsOverlapAndExactCutover() {
        val overlap = verifier(mapOf(SIGNING_KEY_ID to keyPair, "license-signing-v2" to secondKeyPair))
        assertTrue(overlap.verify(token(signingPair = keyPair), device, app) is LicenseTokenVerificationResult.Valid)
        assertTrue(overlap.verify(token(kid = "license-signing-v2", signingPair = secondKeyPair), device, app) is LicenseTokenVerificationResult.Valid)
        assertInvalid(token(kid = "v1"), LicenseTokenInvalidReason.UNKNOWN_KEY)
        assertInvalid(token(signingPair = secondKeyPair), LicenseTokenInvalidReason.INVALID_SIGNATURE)
    }
    @Test fun strictRegistryRejectsBadConfigurationAndSmallRsa() {
        val good = Base64.getEncoder().encodeToString(keyPair.public.encoded)
        assertThrows(IllegalArgumentException::class.java) { TrustedLicenseSigningKeys.fromRegistryJson("[]") }
        assertThrows(org.json.JSONException::class.java) { TrustedLicenseSigningKeys.fromRegistryJson("bad") }
        val duplicate = """[{"kid":"v1","algorithm":"RS256","spkiBase64":"$good"},{"kid":"v1","algorithm":"RS256","spkiBase64":"$good"}]"""
        assertThrows(IllegalArgumentException::class.java) { TrustedLicenseSigningKeys.fromRegistryJson(duplicate) }
        val small = Base64.getEncoder().encodeToString(rsaPair(1024).public.encoded)
        assertThrows(IllegalArgumentException::class.java) {
            TrustedLicenseSigningKeys.fromRegistryJson("""[{"kid":"v1","algorithm":"RS256","spkiBase64":"$small"}]""")
        }
    }

    private fun verifier(pairs: Map<String, KeyPair> = mapOf(SIGNING_KEY_ID to keyPair)) = LicenseTokenVerifier(
        TrustedLicenseSigningKeys.fromBase64Spki(pairs.mapValues { Base64.getEncoder().encodeToString(it.value.public.encoded) }),
        LicenseTimeProvider { now * 1_000L },
    )
    private fun assertInvalid(value: String, expected: LicenseTokenInvalidReason) {
        val result = verifier().verify(value, device, app)
        assertTrue(result is LicenseTokenVerificationResult.Invalid)
        assertEquals(expected, (result as LicenseTokenVerificationResult.Invalid).reason)
    }
    private fun header() = JSONObject().put("alg", "RS256").put("typ", "DWT-LICENSE").put("kid", SIGNING_KEY_ID)
    private fun payload() = JSONObject().put("tokenVersion", 1).put("licenseId", "license-1")
        .put("deviceId", "device-1").put("installationId", device.installationId).put("deviceHash", device.deviceHash)
        .put("packageName", app.packageName).put("issuedAtEpochSeconds", now - 1)
        .put("expiresAtEpochSeconds", now + 100).put("offlineValidUntilEpochSeconds", now + 100)
    private fun token(payload: JSONObject = payload(), header: JSONObject = header(), kid: String? = null,
                      signingPair: KeyPair = keyPair): String {
        if (kid != null) header.put("kid", kid)
        val input = "${encode(header.toString().toByteArray())}.${encode(payload.toString().toByteArray())}"
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(signingPair.private); update(input.toByteArray()); sign() }
        return "$input.${encode(signature)}"
    }
    private fun rsaPair(bits: Int = 2048) = KeyPairGenerator.getInstance("RSA").apply { initialize(bits) }.generateKeyPair()
    private fun encode(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        const val SIGNING_KEY_ID = "license-signing-v1"
    }
}
