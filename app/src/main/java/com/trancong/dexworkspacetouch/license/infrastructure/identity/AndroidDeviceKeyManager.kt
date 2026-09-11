package com.trancong.dexworkspacetouch.license.infrastructure.identity

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.trancong.dexworkspacetouch.license.identity.DeviceIdentityException
import com.trancong.dexworkspacetouch.license.identity.DeviceIdentityFailure
import com.trancong.dexworkspacetouch.license.identity.DeviceKeyManager
import com.trancong.dexworkspacetouch.license.identity.DeviceKeyContract
import com.trancong.dexworkspacetouch.license.identity.PublicKeyEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class AndroidDeviceKeyManager private constructor(
    private val preferences: SharedPreferences,
) : DeviceKeyManager {
    override suspend fun ensureKeyExists(): Unit = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val keyStore = loadKeyStore()
            val hasAlias = try {
                keyStore.containsAlias(KEY_ALIAS)
            } catch (error: GeneralSecurityException) {
                throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE, error)
            }
            if (hasAlias) {
                requireUsableEntry(keyStore)
                markKeyCreated()
                return@synchronized
            }
            if (preferences.getBoolean(DEVICE_KEY_CREATED_KEY, false)) {
                throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE)
            }
            generateKey()
            markKeyCreated()
        }
    }

    override suspend fun getPublicKeyBase64(): String = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val certificate = try {
                loadKeyStore().getCertificate(KEY_ALIAS)
            } catch (error: GeneralSecurityException) {
                throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE, error)
            } ?: throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE)
            try {
                PublicKeyEncoding.x509SubjectPublicKeyInfoBase64(certificate.publicKey.encoded)
            } catch (error: GeneralSecurityException) {
                throw DeviceIdentityException(DeviceIdentityFailure.PUBLIC_KEY_EXPORT_FAILED, error)
            } catch (error: IllegalArgumentException) {
                throw DeviceIdentityException(DeviceIdentityFailure.PUBLIC_KEY_EXPORT_FAILED, error)
            }
        }
    }

    override suspend fun sign(payload: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            val privateKey = try {
                loadKeyStore().getKey(KEY_ALIAS, null)
            } catch (error: GeneralSecurityException) {
                throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE, error)
            } as? PrivateKey ?: throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE)
            try {
                Signature.getInstance(DeviceKeyContract.SIGNATURE_ALGORITHM).run {
                    initSign(privateKey)
                    update(payload)
                    sign()
                }
            } catch (error: GeneralSecurityException) {
                throw DeviceIdentityException(DeviceIdentityFailure.SIGNING_FAILED, error)
            }
        }
    }

    private fun loadKeyStore(): KeyStore = try {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    } catch (error: GeneralSecurityException) {
        throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE, error)
    } catch (error: java.io.IOException) {
        throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE, error)
    }

    private fun requireUsableEntry(keyStore: KeyStore) {
        try {
            val certificate = keyStore.getCertificate(KEY_ALIAS)
            val key = keyStore.getKey(KEY_ALIAS, null)
            if (certificate?.publicKey == null || key !is PrivateKey) {
                throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE)
            }
        } catch (error: GeneralSecurityException) {
            throw DeviceIdentityException(DeviceIdentityFailure.KEY_UNAVAILABLE, error)
        }
    }

    private fun generateKey() {
        try {
            // P-256 is available on minSdk 28 and interoperates with Workers via SHA256withECDSA.
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec(DeviceKeyContract.EC_CURVE))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
            KeyPairGenerator.getInstance(DeviceKeyContract.KEY_ALGORITHM, ANDROID_KEY_STORE).run {
                initialize(spec)
                generateKeyPair()
            }
        } catch (error: GeneralSecurityException) {
            throw DeviceIdentityException(DeviceIdentityFailure.KEY_GENERATION_FAILED, error)
        }
    }

    private fun markKeyCreated() {
        if (!preferences.edit().putBoolean(DEVICE_KEY_CREATED_KEY, true).commit()) {
            throw DeviceIdentityException(DeviceIdentityFailure.KEY_STATE_STORAGE_FAILED)
        }
    }

    companion object {
        const val KEY_ALIAS = DeviceKeyContract.KEY_ALIAS
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private val LOCK = Any()

        fun create(context: Context): AndroidDeviceKeyManager = AndroidDeviceKeyManager(
            context.applicationContext.getSharedPreferences(
                LICENSE_IDENTITY_PREFERENCES,
                Context.MODE_PRIVATE,
            ),
        )
    }
}
