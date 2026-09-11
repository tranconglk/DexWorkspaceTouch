package com.trancong.dexworkspacetouch.license.identity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class DeviceKeyContractTest {
    @Test
    fun cryptoContract_isStableVersionedP256Ecdsa() {
        assertEquals("dexworkspacetouch_license_device_key_v1", DeviceKeyContract.KEY_ALIAS)
        assertEquals("EC", DeviceKeyContract.KEY_ALGORITHM)
        assertEquals("secp256r1", DeviceKeyContract.EC_CURVE)
        assertEquals("SHA256withECDSA", DeviceKeyContract.SIGNATURE_ALGORITHM)
        assertEquals("X.509", DeviceKeyContract.PUBLIC_KEY_FORMAT)
    }

    @Test
    fun publicKeyEncoding_isDeterministicUnwrappedBase64() {
        val bytes = byteArrayOf(0x30, 0x59, 0x30, 0x13, 0x06, 0x07)
        val encoded = PublicKeyEncoding.x509SubjectPublicKeyInfoBase64(bytes)
        assertEquals("MFkwEwYH", encoded)
        assertArrayEquals(bytes, Base64.getDecoder().decode(encoded))
        assertFalse(encoded.contains('\n'))
        assertThrows(IllegalArgumentException::class.java) {
            PublicKeyEncoding.x509SubjectPublicKeyInfoBase64(byteArrayOf())
        }
    }

    @Test
    fun redaction_neverReturnsCompleteIdentifier() {
        val value = "installation-abcdef"
        val redacted = redactIdentifier(value)
        assertNotEquals(value, redacted)
        assertEquals(value.take(4), redacted.take(4))
        assertEquals(value.takeLast(4), redacted.takeLast(4))
        assertNotEquals("abcd", redactIdentifier("abcd"))
    }
}
