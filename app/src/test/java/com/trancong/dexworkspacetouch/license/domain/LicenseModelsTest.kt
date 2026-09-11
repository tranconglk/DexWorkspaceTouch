package com.trancong.dexworkspacetouch.license.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LicenseModelsTest {
    @Test
    fun activationCommand_preservesImmutableValidatedValues() {
        val command = LicenseActivationCommand(
            licenseKey = LicenseKey.parse("APP-7K3M-9QTX-2PL8"),
            device = LicenseDeviceIdentity("installation-1", "device-hash", "public-key"),
            application = LicensedApplicationIdentity(
                packageName = "com.trancong.dexworkspacetouch",
                versionName = "1.0.0",
                versionCode = 3L,
                signingCertificateSha256 = "ABCD",
            ),
        )

        assertEquals("installation-1", command.device.installationId)
        assertEquals("com.trancong.dexworkspacetouch", command.application.packageName)
    }

    @Test
    fun deviceIdentity_rejectsBlankFields() {
        assertThrows(IllegalArgumentException::class.java) { LicenseDeviceIdentity("", "hash", "key") }
        assertThrows(IllegalArgumentException::class.java) { LicenseDeviceIdentity("id", " ", "key") }
        assertThrows(IllegalArgumentException::class.java) { LicenseDeviceIdentity("id", "hash", "\t") }
    }

    @Test
    fun applicationIdentity_rejectsInvalidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            LicensedApplicationIdentity("", "1.0", 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LicensedApplicationIdentity("com.example", "1.0", -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            LicensedApplicationIdentity("com.example", "1.0", 1L, " ")
        }
    }

    @Test
    fun defaultOfflineGrace_isExactlySevenDays() {
        assertEquals(604_800_000L, LicensePolicy.DEFAULT_OFFLINE_GRACE_MILLIS)
        assertEquals(1, LicensePolicy.TOKEN_VERSION)
    }
}
