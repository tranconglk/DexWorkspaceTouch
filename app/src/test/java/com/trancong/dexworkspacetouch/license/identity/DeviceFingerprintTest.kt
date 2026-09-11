package com.trancong.dexworkspacetouch.license.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFingerprintTest {
    private val input = DeviceFingerprintInput("installation-123", "android-456", "com.example.app")

    @Test
    fun canonicalization_isVersionedLengthDelimitedAndDeterministic() {
        val expected = "v1|installationId:16:installation-123" +
            "|androidId:11:android-456|packageName:15:com.example.app"
        assertEquals(expected, DeviceFingerprint.canonicalize(input))
        assertEquals(expected, DeviceFingerprint.canonicalize(input))
    }

    @Test
    fun hash_isStableLowercaseSha256() {
        val first = DeviceFingerprint.sha256(input)
        assertEquals(first, DeviceFingerprint.sha256(input))
        assertTrue(first.matches(Regex("[0-9a-f]{64}")))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.lowercaseHex("abc".toByteArray()),
        )
    }

    @Test
    fun changingAnyIdentityField_changesHash() {
        val original = DeviceFingerprint.sha256(input)
        assertNotEquals(original, DeviceFingerprint.sha256(input.copy(installationId = "other")))
        assertNotEquals(original, DeviceFingerprint.sha256(input.copy(androidId = "other")))
        assertNotEquals(original, DeviceFingerprint.sha256(input.copy(packageName = "other")))
    }

    @Test
    fun input_rejectsBlankFields() {
        assertThrows(IllegalArgumentException::class.java) { input.copy(installationId = " ") }
        assertThrows(IllegalArgumentException::class.java) { input.copy(androidId = "") }
        assertThrows(IllegalArgumentException::class.java) { input.copy(packageName = "\t") }
    }
}
