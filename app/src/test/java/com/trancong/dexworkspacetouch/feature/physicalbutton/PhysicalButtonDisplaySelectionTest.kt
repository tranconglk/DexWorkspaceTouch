package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalButtonDisplaySelectionTest {
    // Metadata thực tế Note 9: API liệt kê phone/HDMI, Activity ở desktop bị thiếu.
    private val note9Available = listOf(
        PhysicalButtonDisplaySnapshot(0, Display.STATE_OFF, 3),
        PhysicalButtonDisplaySnapshot(12, Display.STATE_ON, 11),
    )
    private val note9Host = PhysicalButtonDisplaySnapshot(2, Display.STATE_ON, 536870914)

    @Test fun api29_note9EnumerationOmitsDesktop_selectsObservedHost() {
        assertEquals(2, select(note9Available, listOf(note9Host)))
    }

    @Test fun api29_missingOrDestroyedHost_isBlocked() {
        assertNull(select(note9Available, emptyList()))
    }

    @Test fun api29_phoneHost_isBlocked() {
        assertNull(select(note9Available, listOf(external(Display.DEFAULT_DISPLAY))))
    }

    @Test fun api29_offHost_isBlocked() {
        assertNull(select(note9Available, listOf(note9Host.copy(state = Display.STATE_OFF))))
    }

    @Test fun api29_privateHost_isBlocked() {
        assertNull(select(note9Available, listOf(note9Host.copy(flags = Display.FLAG_PRIVATE))))
    }

    @Test fun api29_multipleObservedExternalHosts_isBlocked() {
        assertNull(select(note9Available, listOf(note9Host, external(3))))
    }

    @Test fun api29_multipleEnumeratedExternalDisplays_isBlocked() {
        assertNull(select(note9Available + external(13), listOf(note9Host)))
    }

    @Test fun api29_duplicateObservationOfSameId_isNotAmbiguous() {
        assertEquals(2, select(note9Available, listOf(note9Host, note9Host)))
    }

    @Test fun api29_phoneObservationAlongsideExternalHost_selectsExternalHost() {
        assertEquals(2, select(note9Available, listOf(external(0), note9Host)))
    }

    @Test fun api29_emptyEnumeration_isBlockedDespiteValidHost() {
        assertNull(select(emptyList(), listOf(note9Host)))
    }

    @Test fun api29_phoneOnlyEnumeration_isBlockedDespiteValidHost() {
        assertNull(select(listOf(external(0)), listOf(note9Host)))
    }

    @Test fun api29_offEnumeratedExternal_isBlockedDespiteValidHost() {
        assertNull(select(listOf(external(12).copy(state = Display.STATE_OFF)), listOf(note9Host)))
    }

    @Test fun api29_privateEnumeratedExternal_isBlockedDespiteValidHost() {
        assertNull(select(listOf(external(12).copy(flags = Display.FLAG_PRIVATE)), listOf(note9Host)))
    }

    @Test fun otherApis_uniqueEnumeration_ignoresDifferentOrAmbiguousObservedHosts() {
        assertEquals(9, select(listOf(external(9)), listOf(note9Host, external(3)), legacy = false))
    }

    @Test fun otherApis_uniqueEnumeration_needsNoObservedHost() {
        assertEquals(9, select(listOf(external(9)), emptyList(), legacy = false))
    }

    @Test fun otherApis_multipleEnumeratedExternalDisplays_isBlocked() {
        assertNull(select(listOf(external(9), external(12)), listOf(note9Host), legacy = false))
    }

    private fun external(id: Int) = PhysicalButtonDisplaySnapshot(id, Display.STATE_ON, 0)

    private fun select(
        available: List<PhysicalButtonDisplaySnapshot>,
        observed: List<PhysicalButtonDisplaySnapshot>,
        legacy: Boolean = true,
    ) = selectPhysicalButtonDisplayId(available, observed, requireObservedHost = legacy)
}
