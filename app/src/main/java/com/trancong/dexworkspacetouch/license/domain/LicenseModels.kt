package com.trancong.dexworkspacetouch.license.domain

data class LicenseDeviceIdentity(
    val installationId: String,
    val deviceHash: String,
    val publicKey: String,
) {
    init {
        require(installationId.isNotBlank()) { "Installation ID must not be blank." }
        require(deviceHash.isNotBlank()) { "Device hash must not be blank." }
        require(publicKey.isNotBlank()) { "Public key must not be blank." }
    }
}

data class LicensedApplicationIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signingCertificateSha256: String? = null,
) {
    init {
        require(packageName.isNotBlank()) { "Package name must not be blank." }
        require(versionName.isNotBlank()) { "Version name must not be blank." }
        require(versionCode >= 0L) { "Version code must not be negative." }
        require(signingCertificateSha256?.isNotBlank() != false) {
            "Signing certificate digest must not be blank when present."
        }
    }
}

data class LicenseActivationCommand(
    val licenseKey: LicenseKey,
    val device: LicenseDeviceIdentity,
    val application: LicensedApplicationIdentity,
)

/** Claims carried by a signed token. This type alone does not prove signature validity. */
data class LicenseTokenClaims(
    val licenseId: String,
    val installationId: String,
    val issuedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val offlineValidUntilEpochSeconds: Long,
    val packageName: String,
    val tokenVersion: Int,
) {
    init {
        require(licenseId.isNotBlank()) { "License ID must not be blank." }
        require(installationId.isNotBlank()) { "Installation ID must not be blank." }
        require(packageName.isNotBlank()) { "Package name must not be blank." }
        require(tokenVersion > 0) { "Token version must be positive." }
        require(issuedAtEpochSeconds >= 0L) { "Issued time must not be negative." }
        require(expiresAtEpochSeconds >= issuedAtEpochSeconds) {
            "Token expiry must not precede issue time."
        }
        require(offlineValidUntilEpochSeconds in issuedAtEpochSeconds..expiresAtEpochSeconds) {
            "Offline validity must be within the token lifetime."
        }
    }
}

object LicensePolicy {
    const val TOKEN_VERSION = 1
    const val DEFAULT_OFFLINE_GRACE_MILLIS = 7L * 24L * 60L * 60L * 1_000L
}

fun interface LicenseTimeProvider {
    fun nowEpochMillis(): Long
}
