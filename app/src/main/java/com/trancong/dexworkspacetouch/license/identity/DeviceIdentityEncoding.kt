package com.trancong.dexworkspacetouch.license.identity

import java.security.MessageDigest
import java.util.Base64

data class DeviceFingerprintInput(
    val installationId: String,
    val androidId: String,
    val packageName: String,
) {
    init {
        require(installationId.isNotBlank()) { "Installation ID must not be blank." }
        require(androidId.isNotBlank()) { "Android ID value must not be blank." }
        require(packageName.isNotBlank()) { "Package name must not be blank." }
    }
}

object DeviceFingerprint {
    const val SCHEMA_VERSION = 1

    fun canonicalize(input: DeviceFingerprintInput): String = buildString {
        append("v").append(SCHEMA_VERSION)
        appendField("installationId", input.installationId)
        appendField("androidId", input.androidId)
        appendField("packageName", input.packageName)
    }

    fun sha256(input: DeviceFingerprintInput): String =
        Sha256.lowercaseHex(canonicalize(input).toByteArray(Charsets.UTF_8))

    private fun StringBuilder.appendField(name: String, value: String) {
        append('|').append(name).append(':').append(value.length).append(':').append(value)
    }
}

object Sha256 {
    fun lowercaseHex(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

object PublicKeyEncoding {
    /** Encodes X.509 SubjectPublicKeyInfo bytes without line wrapping. */
    fun x509SubjectPublicKeyInfoBase64(encoded: ByteArray): String {
        require(encoded.isNotEmpty()) { "Public key bytes must not be empty." }
        return Base64.getEncoder().encodeToString(encoded)
    }
}

fun redactIdentifier(value: String): String = when {
    value.length <= 4 -> "••••"
    else -> "${value.take(4)}…${value.takeLast(4)}"
}
