package com.trancong.dexworkspacetouch.license.identity

enum class DeviceIdentityFailure {
    INSTALLATION_ID_STORAGE_FAILED,
    KEY_STATE_STORAGE_FAILED,
    LOCAL_STATE_CORRUPTED,
    KEY_GENERATION_FAILED,
    KEY_UNAVAILABLE,
    PUBLIC_KEY_EXPORT_FAILED,
    SIGNING_FAILED,
}

class DeviceIdentityException(
    val failure: DeviceIdentityFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)
