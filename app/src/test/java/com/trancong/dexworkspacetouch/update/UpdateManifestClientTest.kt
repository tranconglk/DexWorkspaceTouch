package com.trancong.dexworkspacetouch.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateManifestClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.close() }

    @Test fun newerVersionIsAvailableAndPreservesArtifactMetadata() {
        val result = client(currentVersionCode = 5).parse(manifest(versionCode = 6))
        assertTrue(result is AppUpdateResult.Available)
        val update = (result as AppUpdateResult.Available).update
        assertEquals(6, update.versionCode)
        assertEquals(APK_URL, update.apkUrl)
        assertEquals(SHA, update.apkSha256)
        assertEquals(123456L, update.apkSize)
    }

    @Test fun equalAndOlderVersionsAreCurrent() {
        assertEquals(AppUpdateResult.Current, client(5).parse(manifest(versionCode = 5)))
        assertEquals(AppUpdateResult.Current, client(5).parse(manifest(versionCode = 4)))
    }

    @Test fun malformedOrUntrustedManifestsAreUnavailable() {
        val client = client(4)
        val cases = listOf(
            "not-json",
            manifest(applicationId = "other.package"),
            manifest(apkUrl = "http://downloads.example.com/app.apk"),
            manifest(sha = "bad"),
            manifest(signer = "f".repeat(64)),
            manifest(versionCode = 0),
            manifest().replace("\"publishedAt\":\"2026-09-10T00:00:00Z\",", ""),
        )
        cases.forEach { assertEquals(AppUpdateResult.Unavailable, client.parse(it)) }
    }

    @Test fun serverFailureIsUnavailable() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(AppUpdateResult.Unavailable, client(4, server.url("/update-manifest.json").toString()).check())
    }

    @Test fun networkFailureIsUnavailable() = runTest {
        val url = server.url("/update-manifest.json").toString()
        server.close()
        assertEquals(AppUpdateResult.Unavailable, client(4, url).check())
    }

    private fun client(currentVersionCode: Int, manifestUrl: String = "https://updates.example.com/update-manifest.json") =
        UpdateManifestClient(OkHttpClient(), manifestUrl, currentVersionCode, APPLICATION_ID, SIGNER, true)

    private fun manifest(
        versionCode: Int = 5,
        applicationId: String = APPLICATION_ID,
        apkUrl: String = APK_URL,
        sha: String = SHA,
        signer: String = SIGNER,
    ) = """{"schemaVersion":1,"applicationId":"$applicationId","versionName":"1.0.1","versionCode":$versionCode,"apkUrl":"$apkUrl","apkSha256":"$sha","apkSize":123456,"signingCertificateSha256":"$signer","publishedAt":"2026-09-10T00:00:00Z","releaseNotes":"Test release"}"""

    private companion object {
        const val APPLICATION_ID = "com.trancong.dexworkspacetouch"
        const val APK_URL = "https://downloads.example.com/DexWorkspaceTouch.apk"
        const val SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        const val SIGNER = "19ac0ea99125361b3c2083aaa44c8745ebad9c55fa967642c2b9e5a1086a45e7"
    }
}
