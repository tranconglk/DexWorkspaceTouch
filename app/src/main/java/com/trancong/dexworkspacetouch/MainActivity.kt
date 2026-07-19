package com.trancong.dexworkspacetouch

import android.os.Bundle
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.trancong.dexworkspacetouch.navigation.TouchNavigation
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferDetector
import com.trancong.dexworkspacetouch.workspace.externaltransfer.AndroidExternalTransferIntentAdapter
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferIntentParser
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferIntentResult
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferReadFailure
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferTooLargeException
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferViewModel
import com.trancong.dexworkspacetouch.workspace.externaltransfer.readExternalTransferBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val externalTransferViewModel: ExternalTransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DeX-only launch/display gating intentionally waits for the legacy-flow audit.
        enableEdgeToEdge()
        setContent {
            DexWorkspaceTouchTheme {
                TouchNavigation(activity = this, externalTransferViewModel = externalTransferViewModel)
            }
        }
        handleExternalIntent(intent, allowConsumedReplay = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent, allowConsumedReplay = true)
    }

    private fun handleExternalIntent(sourceIntent: Intent, allowConsumedReplay: Boolean) {
        val payload = AndroidExternalTransferIntentAdapter.from(sourceIntent)
        val parsed = ExternalTransferIntentParser.parse(payload)
        if (parsed is ExternalTransferIntentResult.UnsupportedAction) return
        val identity = "${sourceIntent.action}|${(parsed as? ExternalTransferIntentResult.SingleUri)?.uri.orEmpty()}"
        if (!externalTransferViewModel.begin(identity, allowConsumedReplay)) return
        if (parsed !is ExternalTransferIntentResult.SingleUri) {
            val failure = when (parsed) {
                ExternalTransferIntentResult.MissingUri -> ExternalTransferReadFailure.MISSING_URI
                ExternalTransferIntentResult.MultipleUris -> ExternalTransferReadFailure.MULTIPLE_URIS
                ExternalTransferIntentResult.InvalidPayload -> ExternalTransferReadFailure.INVALID_PAYLOAD
                else -> return
            }
            externalTransferViewModel.fail(identity, failure)
            return
        }
        val uri = android.net.Uri.parse(parsed.uri)
        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use(::readExternalTransferBytes)
                        ?: throw java.io.IOException("Provider returned no stream")
                }
                val detection = ExternalTransferDetector.detect(bytes)
                externalTransferViewModel.accept(identity, bytes, detection)
                debugDiagnostic(sourceIntent, payload, displayNameExtension(uri), detection.javaClass.simpleName)
            } catch (error: CancellationException) { throw error }
            catch (_: ExternalTransferTooLargeException) {
                externalTransferViewModel.fail(identity, ExternalTransferReadFailure.FILE_TOO_LARGE)
            } catch (_: SecurityException) {
                externalTransferViewModel.fail(identity, ExternalTransferReadFailure.PERMISSION_REVOKED)
            } catch (_: Exception) {
                externalTransferViewModel.fail(identity, ExternalTransferReadFailure.READ_FAILURE)
            }
        }
    }

    private fun displayNameExtension(uri: android.net.Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)?.substringAfterLast('.', "")?.takeIf(String::isNotBlank)
        }
    }.getOrNull()

    private fun debugDiagnostic(
        intent: Intent,
        payload: com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferIntentPayload,
        extension: String?,
        detected: String,
    ) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Log.d(
                DIAGNOSTIC_TAG,
                "action=${intent.action ?: "unknown"} mime=${intent.type ?: "unknown"} " +
                    "extension=${extension ?: "unknown"} extraStream=${payload.extraStreamUri != null} " +
                    "clipItems=${payload.clipUris.size} grantFlags=${intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION} " +
                    "detected=$detected",
            )
        }
    }

    private companion object {
        const val DIAGNOSTIC_TAG = "DWT_EXTERNAL_FILE"
    }
}
