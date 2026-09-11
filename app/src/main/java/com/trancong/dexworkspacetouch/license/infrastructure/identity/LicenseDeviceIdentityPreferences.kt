package com.trancong.dexworkspacetouch.license.infrastructure.identity

import android.content.Context
import android.content.SharedPreferences
import com.trancong.dexworkspacetouch.license.identity.DeviceIdentityException
import com.trancong.dexworkspacetouch.license.identity.DeviceIdentityFailure
import com.trancong.dexworkspacetouch.license.identity.InstallationIdProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal const val LICENSE_IDENTITY_PREFERENCES = "license_device_identity"
internal const val INSTALLATION_ID_KEY = "installation_id_v1"
internal const val DEVICE_KEY_CREATED_KEY = "device_key_created_v1"

class SharedPreferencesInstallationIdProvider private constructor(
    private val preferences: SharedPreferences,
    private val idGenerator: () -> String,
) : InstallationIdProvider {
    override suspend fun getOrCreate(): String = withContext(Dispatchers.IO) {
        synchronized(LOCK) {
            if (preferences.contains(INSTALLATION_ID_KEY)) {
                val stored = preferences.getString(INSTALLATION_ID_KEY, null)
                if (stored.isNullOrBlank()) {
                    throw DeviceIdentityException(DeviceIdentityFailure.LOCAL_STATE_CORRUPTED)
                }
                return@synchronized stored
            }
            val generated = idGenerator()
            if (generated.isBlank()) {
                throw DeviceIdentityException(DeviceIdentityFailure.LOCAL_STATE_CORRUPTED)
            }
            if (!preferences.edit().putString(INSTALLATION_ID_KEY, generated).commit()) {
                throw DeviceIdentityException(DeviceIdentityFailure.INSTALLATION_ID_STORAGE_FAILED)
            }
            generated
        }
    }

    companion object {
        private val LOCK = Any()

        fun create(context: Context): SharedPreferencesInstallationIdProvider =
            SharedPreferencesInstallationIdProvider(
                preferences = context.applicationContext.getSharedPreferences(
                    LICENSE_IDENTITY_PREFERENCES,
                    Context.MODE_PRIVATE,
                ),
                idGenerator = { UUID.randomUUID().toString() },
            )
    }
}
