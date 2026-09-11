package com.trancong.dexworkspacetouch.license.identity

import com.trancong.dexworkspacetouch.license.domain.LicenseDeviceIdentity

fun interface InstallationIdProvider {
    suspend fun getOrCreate(): String
}

interface DeviceKeyManager {
    suspend fun ensureKeyExists()
    suspend fun getPublicKeyBase64(): String
    suspend fun sign(payload: ByteArray): ByteArray
}

fun interface DeviceFingerprintSource {
    fun read(installationId: String): DeviceFingerprintInput
}

fun interface DeviceIdentityProvider {
    suspend fun get(): LicenseDeviceIdentity
}

class DefaultDeviceIdentityProvider(
    private val installationIdProvider: InstallationIdProvider,
    private val deviceKeyManager: DeviceKeyManager,
    private val fingerprintSource: DeviceFingerprintSource,
) : DeviceIdentityProvider {
    override suspend fun get(): LicenseDeviceIdentity {
        val installationId = installationIdProvider.getOrCreate()
        deviceKeyManager.ensureKeyExists()
        val input = fingerprintSource.read(installationId)
        return LicenseDeviceIdentity(
            installationId = installationId,
            deviceHash = DeviceFingerprint.sha256(input),
            publicKey = deviceKeyManager.getPublicKeyBase64(),
        )
    }
}
