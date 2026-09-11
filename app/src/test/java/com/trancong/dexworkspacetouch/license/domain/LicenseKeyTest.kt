package com.trancong.dexworkspacetouch.license.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LicenseKeyTest {
    @Test
    fun parse_normalizesWhitespaceAndCase() {
        assertEquals("APP-7K3M-9QTX-2PL8", LicenseKey.parse(" app-7k3m-9qtx-2pl8 ").value)
    }

    @Test
    fun parse_rejectsMalformedValues() {
        listOf("", "APP-1234", "APP-1234-5678-90", "APP_1234_5678_9ABC").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { LicenseKey.parse(value) }
        }
    }

    @Test
    fun stringRepresentationsDoNotExposeFullKey() {
        val key = LicenseKey.parse("APP-7K3M-9QTX-2PL8")
        assertEquals("APP-••••-••••-2PL8", key.redacted())
        assertFalse(key.toString().contains("7K3M"))
        assertFalse(key.toString().contains("9QTX"))
    }
}
