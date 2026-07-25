package com.trancong.dexworkspacetouch.about.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutDialogStateTest {
    @Test
    fun `open and close dialog`() {
        val opened = reduceAboutDialog(AboutDialogState(), AboutDialogEvent.Open)
        assertTrue(opened.state.isOpen)
        assertFalse(reduceAboutDialog(opened.state, AboutDialogEvent.Close).state.isOpen)
    }

    @Test
    fun `copy request emits exactly one clipboard command`() {
        val transition = reduceAboutDialog(AboutDialogState(isOpen = true), AboutDialogEvent.CopyRequested)
        assertEquals(AboutDialogEffect.CopyDiagnostics, transition.effect)
        assertNull(reduceAboutDialog(transition.state, AboutDialogEvent.HostResized).effect)
    }

    @Test
    fun `resize preserves state and does not copy again`() {
        val state = AboutDialogState(isOpen = true, copySuccessVisible = true)
        val transition = reduceAboutDialog(state, AboutDialogEvent.HostResized)
        assertEquals(state, transition.state)
        assertNull(transition.effect)
    }

    @Test
    fun `copy success shows feedback`() {
        val transition = reduceAboutDialog(
            AboutDialogState(isOpen = true),
            AboutDialogEvent.CopySucceeded,
        )
        assertTrue(transition.state.copySuccessVisible)
        assertTrue(transition.state.isOpen)
    }
}
