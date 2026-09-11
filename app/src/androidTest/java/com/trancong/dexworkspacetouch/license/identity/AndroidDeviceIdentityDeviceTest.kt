package com.trancong.dexworkspacetouch.license.identity

import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.license.infrastructure.identity.AndroidDeviceIdentityProvider
import com.trancong.dexworkspacetouch.license.infrastructure.identity.AndroidDeviceKeyManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class AndroidDeviceIdentityDeviceTest {
    @Test
    fun identityAndKey_areStableAndGeneratedSignatureVerifies() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = AndroidDeviceIdentityProvider.create(context)
        val first = provider.get()
        val second = provider.get()

        assertEquals(first, second)
        assertTrue(first.installationId.isNotBlank())
        assertTrue(first.deviceHash.matches(Regex("[0-9a-f]{64}")))

        val payload = "lic-002-device-signature-proof".toByteArray()
        val keyManager = AndroidDeviceKeyManager.create(context)
        val signatureBytes = keyManager.sign(payload)
        val publicKey = KeyFactory.getInstance(DeviceKeyContract.KEY_ALGORITHM).generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(first.publicKey)),
        )
        val verified = Signature.getInstance(DeviceKeyContract.SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(payload)
            verify(signatureBytes)
        }

        assertTrue(verified)
    }

    @Test
    fun challengeProof_isP256Sha256AndUsesDerWireEncoding() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val identity = AndroidDeviceIdentityProvider.create(context).get()
        val challenge = ByteArray(32) { index -> (index + 1).toByte() }
        val signature = AndroidDeviceKeyManager.create(context).sign(challenge)

        assertDerEcdsaSignature(signature)
        val publicKey = KeyFactory.getInstance(DeviceKeyContract.KEY_ALGORITHM).generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(identity.publicKey)),
        )
        assertTrue(Signature.getInstance(DeviceKeyContract.SIGNATURE_ALGORITHM).run {
            initVerify(publicKey); update(challenge); verify(signature)
        })
    }

    private fun assertDerEcdsaSignature(value: ByteArray) {
        assertTrue(value.size in 8..72)
        assertEquals(0x30, value[0].toInt() and 0xff)
        assertEquals(value.size - 2, value[1].toInt() and 0xff)
        var offset = 2
        repeat(2) {
            assertEquals(0x02, value[offset++].toInt() and 0xff)
            val length = value[offset++].toInt() and 0xff
            assertTrue(length in 1..33)
            assertTrue(offset + length <= value.size)
            offset += length
        }
        assertEquals(value.size, offset)
    }
}
