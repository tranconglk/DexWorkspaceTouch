package com.trancong.dexworkspacetouch.workspace.apppicker.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InstalledAppTest {
    @Test fun blankPackageNameIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InstalledApp(" ", null, "Example", false)
        }
    }

    @Test fun blankLabelIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InstalledApp("com.example", null, "\t", false)
        }
    }

    @Test fun nullActivityIsValidForNonLaunchableApp() {
        val app = InstalledApp("com.example", null, "Example", false)
        assertEquals(null, app.activityName)
    }

    @Test fun equalityAndIdentityAreDeterministic() {
        val first = InstalledApp("com.example", "com.example.Main", "Example", true)
        val second = InstalledApp("com.example", "com.example.Main", "Example", true)

        assertEquals(first, second)
        assertEquals(AppIdentity("com.example", "com.example.Main"), first.identity)
    }
}
