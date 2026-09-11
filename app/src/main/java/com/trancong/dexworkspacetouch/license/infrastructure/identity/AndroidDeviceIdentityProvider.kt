package com.trancong.dexworkspacetouch.license.infrastructure.identity

import android.content.Context
import android.provider.Settings
import com.trancong.dexworkspacetouch.license.domain.LicenseDeviceIdentity
import com.trancong.dexworkspacetouch.license.identity.DefaultDeviceIdentityProvider
import com.trancong.dexworkspacetouch.license.identity.DeviceFingerprintInput
import com.trancong.dexworkspacetouch.license.identity.DeviceFingerprintSource
import com.trancong.dexworkspacetouch.license.identity.DeviceIdentityProvider

class AndroidDeviceIdentityProvider private constructor(
    private val delegate: DeviceIdentityProvider,
) : DeviceIdentityProvider {
    override suspend fun get(): LicenseDeviceIdentity = delegate.get()

    companion object {
        private const val ANDROID_ID_UNAVAILABLE = "unavailable"

        fun create(context: Context): AndroidDeviceIdentityProvider {
            val appContext = context.applicationContext
            return AndroidDeviceIdentityProvider(
                DefaultDeviceIdentityProvider(
                    installationIdProvider = SharedPreferencesInstallationIdProvider.create(appContext),
                    deviceKeyManager = AndroidDeviceKeyManager.create(appContext),
                    fingerprintSource = DeviceFingerprintSource { installationId ->
                        DeviceFingerprintInput(
                            installationId = installationId,
                            androidId = Settings.Secure.getString(
                                appContext.contentResolver,
                                Settings.Secure.ANDROID_ID,
                            )?.takeIf(String::isNotBlank) ?: ANDROID_ID_UNAVAILABLE,
                            packageName = appContext.packageName,
                        )
                    },
                ),
            )
        }
    }
}
