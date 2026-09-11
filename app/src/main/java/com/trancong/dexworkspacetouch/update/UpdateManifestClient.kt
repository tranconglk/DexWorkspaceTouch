package com.trancong.dexworkspacetouch.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI

class UpdateManifestClient(
    private val client: OkHttpClient,
    private val manifestUrl: String,
    private val currentVersionCode: Int,
    private val expectedApplicationId: String,
    private val expectedSignerSha256: String,
    private val allowCleartextManifestForDebug: Boolean = false,
) : AppUpdateRepository {
    override suspend fun check(): AppUpdateResult = withContext(Dispatchers.IO) {
        if (!isAllowedManifestUrl(manifestUrl)) return@withContext AppUpdateResult.Unavailable
        try {
            client.newCall(Request.Builder().url(manifestUrl).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext AppUpdateResult.Unavailable
                parse(response.body.string())
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            AppUpdateResult.Unavailable
        }
    }

    internal fun parse(json: String): AppUpdateResult = try {
        val root = JSONObject(json)
        require(root.get("schemaVersion") is Number && root.getInt("schemaVersion") == 1)
        require(root.getString("applicationId") == expectedApplicationId)
        require(root.get("versionCode") is Number)
        val code = root.getInt("versionCode").also { require(it > 0) }
        val versionName = root.getString("versionName").also { require(it.isNotBlank()) }
        val url = root.getString("apkUrl").also { require(isHttps(it)) }
        val sha = root.getString("apkSha256").lowercase().also { require(SHA256.matches(it)) }
        require(root.get("apkSize") is Number)
        val size = root.getLong("apkSize").also { require(it in 1..MAX_APK_SIZE) }
        require(root.getString("signingCertificateSha256").lowercase() == expectedSignerSha256)
        require(root.getString("publishedAt").isNotBlank())
        val update = AppUpdate(versionName, code, url, sha, size,
            root.optString("releaseNotes").take(MAX_NOTES))
        if (code > currentVersionCode) AppUpdateResult.Available(update) else AppUpdateResult.Current
    } catch (_: Exception) {
        AppUpdateResult.Unavailable
    }

    private fun isHttps(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.host != "localhost" && uri.host != "10.0.2.2"
    } catch (_: Exception) { false }

    private fun isAllowedManifestUrl(value: String): Boolean = try {
        val uri = URI(value)
        val validHost = !uri.host.isNullOrBlank()
        (isHttps(value) || (allowCleartextManifestForDebug && uri.scheme == "http" && validHost))
    } catch (_: Exception) { false }

    private companion object {
        val SHA256 = Regex("^[0-9a-f]{64}$")
        const val MAX_APK_SIZE = 1_000_000_000L
        const val MAX_NOTES = 2_000
    }
}
