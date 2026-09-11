package com.trancong.dexworkspacetouch.license.identity

/** Stable cryptographic identifiers shared by the domain boundary and Android adapter. */
object DeviceKeyContract {
    const val KEY_ALIAS = "dexworkspacetouch_license_device_key_v1"
    const val KEY_ALGORITHM = "EC"
    const val EC_CURVE = "secp256r1"
    const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    const val PUBLIC_KEY_FORMAT = "X.509"
}
