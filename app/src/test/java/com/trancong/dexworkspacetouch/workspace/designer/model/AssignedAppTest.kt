package com.trancong.dexworkspacetouch.workspace.designer.model

import org.junit.Assert.assertThrows
import org.junit.Test

class AssignedAppTest {
    @Test fun blankRequiredFieldsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AssignedApp("", "com.example.MainActivity", "Example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssignedApp("com.example", " ", "Example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AssignedApp("com.example", "com.example.MainActivity", "\t")
        }
    }

    @Test fun nullActivityIsAcceptedDuringIdentityMigration() {
        AssignedApp("com.example", null, "Example")
    }
}
