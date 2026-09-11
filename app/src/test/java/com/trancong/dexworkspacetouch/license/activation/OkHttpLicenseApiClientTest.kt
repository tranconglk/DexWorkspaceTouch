package com.trancong.dexworkspacetouch.license.activation

import com.trancong.dexworkspacetouch.license.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class OkHttpLicenseApiClientTest {
    private lateinit var server: MockWebServer
    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }
    @Test fun challengePostsExactIdentityContractAndParsesNonce() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"data":{"challengeId":"cid","challenge":"abc","expiresAt":120,"serverTime":1,"proofVersion":1}}""").addHeader("X-Request-ID", "r1"))
        assertTrue(client().requestChallenge(command()) is LicenseChallengeApiResult.Success)
        val request = server.takeRequest(); val body = JSONObject(request.body.readUtf8())
        assertEquals("/v1/license/challenge", request.path); assertEquals("APP-AAAA-BBBB-CCCC", body.getString("licenseKey"))
        assertEquals("iid", body.getJSONObject("device").getString("installationId"))
        assertEquals("com.example.app", body.getJSONObject("application").getString("packageName"))
    }
    @Test fun proofPostsOnlyVersionChallengeIdAndDerSignature() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"data":{"licenseToken":"signed"}}"""))
        assertEquals(LicenseApiResult.Success("signed", null), client().activate(LicenseActivationProof("cid", "der")))
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals(setOf("proofVersion", "challengeId", "signature"), body.keys().asSequence().toSet())
        assertEquals("cid", body.getString("challengeId")); assertEquals("der", body.getString("signature"))
    }
    @Test fun challengeAndProofErrorsMapStableCodes() = runBlocking {
        server.enqueue(json(409, """{"ok":false,"error":{"code":"CHALLENGE_USED"}}"""))
        assertEquals(LicenseErrorCode.CHALLENGE_USED, (client().requestChallenge(command()) as LicenseChallengeApiResult.Rejected).code)
        server.enqueue(json(403, """{"ok":false,"error":{"code":"PROOF_INVALID"}}"""))
        assertEquals(LicenseErrorCode.PROOF_INVALID, (client().activate(LicenseActivationProof("cid", "der")) as LicenseApiResult.Rejected).code)
    }
    @Test fun malformedResponsesAndUnsafeProductionUrlFailClosed() = runBlocking {
        server.enqueue(json(200, "not-json")); assertTrue(client().requestChallenge(command()) is LicenseChallengeApiResult.InvalidResponse)
        server.enqueue(json(200, """{"ok":true,"data":{}}""")); assertTrue(client().activate(LicenseActivationProof("c", "s")) is LicenseApiResult.InvalidResponse)
        var failed = false; try { OkHttpLicenseApiClient("http://example.com", false) } catch (_: IllegalArgumentException) { failed = true }; assertTrue(failed)
    }
    @Test fun refreshUsesBearerOnlyForChallengeAndProofContainsNoIdentityOrLicenseKey() = runBlocking {
        server.enqueue(json(200, """{"ok":true,"data":{"challengeId":"rcid","challenge":"abc","expiresAt":120,"serverTime":1,"proofVersion":1}}"""))
        val application = LicensedApplicationIdentity("com.example.app", "1.0", 2, "c".repeat(64))
        assertTrue(client().requestRefreshChallenge("current.signed.token", application) is LicenseChallengeApiResult.Success)
        val challenge = server.takeRequest()
        assertEquals("/v1/license/refresh/challenge", challenge.path)
        assertEquals("Bearer current.signed.token", challenge.getHeader("Authorization"))
        assertEquals(setOf("packageName", "signingCertificateSha256"),
            JSONObject(challenge.body.readUtf8()).keys().asSequence().toSet())
        server.enqueue(json(200, """{"ok":true,"data":{"licenseToken":"renewed"}}"""))
        assertEquals(LicenseApiResult.Success("renewed", null), client().refresh(LicenseActivationProof("rcid", "der")))
        val proof = server.takeRequest()
        assertNull(proof.getHeader("Authorization"))
        assertEquals(setOf("proofVersion", "challengeId", "signature"),
            JSONObject(proof.body.readUtf8()).keys().asSequence().toSet())
    }
    @Test fun http429MapsToStableRateLimitedForActivationAndRefresh() = runBlocking {
        server.enqueue(json(429, """{"ok":false,"error":{"code":"RATE_LIMITED","message":"Too many requests."}}""").addHeader("Retry-After", "60"))
        assertEquals(LicenseErrorCode.RATE_LIMITED,
            (client().requestChallenge(command()) as LicenseChallengeApiResult.Rejected).code)
        server.enqueue(json(429, """{"ok":false,"error":{"code":"RATE_LIMITED","message":"Too many requests."}}"""))
        assertEquals(LicenseErrorCode.RATE_LIMITED,
            (client().requestRefreshChallenge("token", command().application) as LicenseChallengeApiResult.Rejected).code)
    }
    private fun client() = OkHttpLicenseApiClient(server.url("/").toString(), true)
    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json; charset=utf-8").setBody(body)
    private fun command() = LicenseActivationCommand(LicenseKey.parse("APP-AAAA-BBBB-CCCC"), LicenseDeviceIdentity("iid", "a".repeat(64), "public"), LicensedApplicationIdentity("com.example.app", "1.0", 2, "c".repeat(64)))
}
