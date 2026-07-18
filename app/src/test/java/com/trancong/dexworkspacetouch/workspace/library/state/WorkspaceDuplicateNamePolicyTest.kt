package com.trancong.dexworkspacetouch.workspace.library.state

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceDuplicateNamePolicyTest {
    @Test fun `first copy uses unnumbered suffix`() {
        assertEquals("Đi đường (Bản sao)", WorkspaceDuplicateNamePolicy.nextName("Đi đường", emptyList()))
    }

    @Test fun `next copy uses smallest available number including gaps`() {
        assertEquals(
            "Đi đường (Bản sao 2)",
            WorkspaceDuplicateNamePolicy.nextName("Đi đường", listOf("Đi đường (Bản sao)", "Đi đường (Bản sao 3)")),
        )
    }

    @Test fun `copy suffix is normalized to original base`() {
        assertEquals(
            "Đi đường (Bản sao 3)",
            WorkspaceDuplicateNamePolicy.nextName(
                "Đi đường (Bản sao 2)",
                listOf("Đi đường (Bản sao)", "Đi đường (Bản sao 2)"),
            ),
        )
    }

    @Test fun `comparison ignores case and surrounding whitespace`() {
        assertEquals(
            "Đi đường (Bản sao 2)",
            WorkspaceDuplicateNamePolicy.nextName("  Đi đường  ", listOf(" đi ĐƯỜNG (bản SAO) ")),
        )
    }

    @Test fun `result is deterministic`() {
        val names = listOf("Name (Bản sao)")
        assertEquals(
            WorkspaceDuplicateNamePolicy.nextName("Name", names),
            WorkspaceDuplicateNamePolicy.nextName("Name", names),
        )
    }
}
