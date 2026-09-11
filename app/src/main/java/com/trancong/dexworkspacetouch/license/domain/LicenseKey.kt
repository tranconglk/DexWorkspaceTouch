package com.trancong.dexworkspacetouch.license.domain

/** A normalized, user-entered license key. Generation remains a backend responsibility. */
@ConsistentCopyVisibility
data class LicenseKey private constructor(val value: String) {
    companion object {
        private const val MAX_LENGTH = 128
        private val SUPPORTED_FORMAT = Regex("[A-Z][A-Z0-9]{1,15}(?:-[A-Z0-9]{4}){3}")

        fun parse(rawValue: String): LicenseKey {
            val normalized = rawValue.trim().uppercase()
            require(normalized.length <= MAX_LENGTH) { "License key is too long." }
            require(SUPPORTED_FORMAT.matches(normalized)) { "License key format is invalid." }
            return LicenseKey(normalized)
        }
    }

    /** Avoids accidentally exposing a complete key in UI diagnostics or logs. */
    fun redacted(): String = "${value.substringBefore('-')}-••••-••••-${value.takeLast(4)}"

    override fun toString(): String = redacted()
}
