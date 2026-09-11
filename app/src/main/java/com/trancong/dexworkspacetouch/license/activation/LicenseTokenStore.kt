package com.trancong.dexworkspacetouch.license.activation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface LicenseTokenStore {
    suspend fun load(): String?
    suspend fun saveVerified(token: String)
    suspend fun clear()
}

class SharedPreferencesLicenseTokenStore private constructor(context: Context) : LicenseTokenStore {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override suspend fun load(): String? = withContext(Dispatchers.IO) {
        preferences.getString(KEY_TOKEN, null)
    }

    override suspend fun saveVerified(token: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(KEY_TOKEN, token).commit()) { "Unable to persist license token." }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(KEY_TOKEN).commit()) { "Unable to clear license token." }
    }

    companion object {
        private const val FILE_NAME = "license_token"
        private const val KEY_TOKEN = "verified_token"
        fun create(context: Context): LicenseTokenStore = SharedPreferencesLicenseTokenStore(context.applicationContext)
    }
}
