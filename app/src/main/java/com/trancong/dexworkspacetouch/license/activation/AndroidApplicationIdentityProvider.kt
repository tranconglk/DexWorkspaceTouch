package com.trancong.dexworkspacetouch.license.activation

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.trancong.dexworkspacetouch.license.domain.LicensedApplicationIdentity
import java.security.MessageDigest

fun interface ApplicationIdentityProvider {
    fun get(): LicensedApplicationIdentity
}

class AndroidApplicationIdentityProvider private constructor(private val context: Context) : ApplicationIdentityProvider {
    override fun get(): LicensedApplicationIdentity {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = requireNotNull(packageInfo.signingInfo)
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners.orEmpty()
            else signingInfo.signingCertificateHistory.orEmpty()
        } else {
            @Suppress("DEPRECATION") packageInfo.signatures.orEmpty()
        }
        val digest = signatures.firstOrNull()?.toByteArray()?.let { certificate ->
            MessageDigest.getInstance("SHA-256").digest(certificate)
                .joinToString("") { "%02x".format(it) }
        }
        return LicensedApplicationIdentity(
            packageName = packageInfo.packageName,
            versionName = packageInfo.versionName.orEmpty().ifBlank { "unknown" },
            versionCode = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode
            else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            signingCertificateSha256 = digest,
        )
    }

    companion object {
        fun create(context: Context) = AndroidApplicationIdentityProvider(context.applicationContext)
    }
}
