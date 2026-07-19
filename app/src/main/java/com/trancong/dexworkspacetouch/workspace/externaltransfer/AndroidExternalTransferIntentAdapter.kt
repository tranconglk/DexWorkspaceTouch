package com.trancong.dexworkspacetouch.workspace.externaltransfer

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

object AndroidExternalTransferIntentAdapter {
    fun from(intent: Intent): ExternalTransferIntentPayload {
        val hasStreamExtra = intent.hasExtra(Intent.EXTRA_STREAM)
        val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val clipUris = buildList {
            val clipData = intent.clipData ?: return@buildList
            repeat(clipData.itemCount) { index ->
                clipData.getItemAt(index).uri?.let { add(it.toString()) }
            }
        }
        return ExternalTransferIntentPayload(
            action = intent.action,
            dataUri = intent.data?.toString(),
            extraStreamUri = streamUri?.toString(),
            clipUris = clipUris,
            invalidStreamPayload = hasStreamExtra && streamUri == null,
        )
    }
}
