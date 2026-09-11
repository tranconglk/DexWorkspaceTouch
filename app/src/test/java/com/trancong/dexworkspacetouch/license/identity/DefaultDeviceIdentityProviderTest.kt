package com.trancong.dexworkspacetouch.license.identity

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceIdentityProviderTest {
    @Test
    fun identity_usesPersistentIdFingerprintAndPublicKey() = runBlocking {
        val keyManager = FakeDeviceKeyManager()
        var fingerprintInstallationId: String? = null
        val provider = DefaultDeviceIdentityProvider(
            installationIdProvider = InstallationIdProvider { "installation-stable" },
            deviceKeyManager = keyManager,
            fingerprintSource = DeviceFingerprintSource { installationId ->
                fingerprintInstallationId = installationId
                DeviceFingerprintInput(installationId, "android-id", "com.example")
            },
        )
        val identity = provider.get()
        assertEquals("installation-stable", identity.installationId)
        assertEquals("installation-stable", fingerprintInstallationId)
        assertEquals("public-key-base64", identity.publicKey)
        assertTrue(identity.deviceHash.matches(Regex("[0-9a-f]{64}")))
        assertEquals(1, keyManager.ensureCalls)
    }

    @Test
    fun signingApi_delegatesPayloadWithoutPrivateKeyExposure() = runBlocking {
        val manager: DeviceKeyManager = FakeDeviceKeyManager()
        assertEquals(listOf<Byte>(3, 2, 1), manager.sign(byteArrayOf(1, 2, 3)).toList())
    }

    private class FakeDeviceKeyManager : DeviceKeyManager {
        var ensureCalls = 0
        override suspend fun ensureKeyExists() { ensureCalls++ }
        override suspend fun getPublicKeyBase64(): String = "public-key-base64"
        override suspend fun sign(payload: ByteArray): ByteArray = payload.reversedArray()
    }
}
