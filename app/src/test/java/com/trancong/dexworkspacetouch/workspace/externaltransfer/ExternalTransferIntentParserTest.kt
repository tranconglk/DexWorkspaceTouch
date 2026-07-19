package com.trancong.dexworkspacetouch.workspace.externaltransfer

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalTransferIntentParserTest {
    private val one = "content://provider/one.dwt"
    private val two = "content://provider/two.dwtbundle"

    @Test fun actionSendUsesExtraStream() {
        assertEquals(
            ExternalTransferIntentResult.SingleUri(one),
            parse(extra = one),
        )
    }

    @Test fun actionSendFallsBackToClipData() {
        assertEquals(
            ExternalTransferIntentResult.SingleUri(one),
            parse(clips = listOf(one)),
        )
    }

    @Test fun sameExtraAndClipUriProducesOneResult() {
        assertEquals(
            ExternalTransferIntentResult.SingleUri(one),
            parse(extra = one, clips = listOf(one)),
        )
    }

    @Test fun differentExtraAndClipUrisAreRejected() {
        assertEquals(ExternalTransferIntentResult.MultipleUris, parse(extra = one, clips = listOf(two)))
    }

    @Test fun missingUriIsTyped() {
        assertEquals(ExternalTransferIntentResult.MissingUri, parse())
    }

    @Test fun multipleDistinctClipItemsAreRejected() {
        assertEquals(ExternalTransferIntentResult.MultipleUris, parse(clips = listOf(one, two)))
    }

    @Test fun duplicateClipItemsStillProduceOneResult() {
        assertEquals(ExternalTransferIntentResult.SingleUri(one), parse(clips = listOf(one, one)))
    }

    @Test fun unsupportedActionIsTyped() {
        assertEquals(
            ExternalTransferIntentResult.UnsupportedAction,
            ExternalTransferIntentParser.parse(ExternalTransferIntentPayload(action = "other")),
        )
    }

    @Test fun nonContentAndMalformedStreamPayloadsAreRejected() {
        assertEquals(ExternalTransferIntentResult.InvalidPayload, parse(extra = "file:///tmp/one.dwt"))
        assertEquals(
            ExternalTransferIntentResult.InvalidPayload,
            ExternalTransferIntentParser.parse(
                ExternalTransferIntentPayload(
                    action = ExternalTransferIntentParser.ActionSend,
                    invalidStreamPayload = true,
                ),
            ),
        )
    }

    @Test fun actionViewUsesSameParserBoundary() {
        assertEquals(
            ExternalTransferIntentResult.SingleUri(one),
            ExternalTransferIntentParser.parse(
                ExternalTransferIntentPayload(
                    action = ExternalTransferIntentParser.ActionView,
                    dataUri = one,
                ),
            ),
        )
    }

    private fun parse(extra: String? = null, clips: List<String> = emptyList()) =
        ExternalTransferIntentParser.parse(
            ExternalTransferIntentPayload(
                action = ExternalTransferIntentParser.ActionSend,
                extraStreamUri = extra,
                clipUris = clips,
            ),
        )
}
