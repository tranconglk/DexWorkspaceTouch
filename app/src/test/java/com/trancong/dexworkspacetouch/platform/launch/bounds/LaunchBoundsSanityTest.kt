package com.trancong.dexworkspacetouch.platform.launch.bounds

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchBoundsSanityTest {
    private val workArea = DisplayWorkArea(
        widthPx = 1920,
        heightPx = 1080,
        insetLeftPx = 10,
        insetTopPx = 20,
        insetRightPx = 30,
        insetBottomPx = 40,
    )

    @Test fun `rect inside usable area is valid`() = assertValid(10, 20, 1890, 1040)

    @Test fun `rect beyond left is invalid`() = assertInvalid(9, 20, 1890, 1040)

    @Test fun `rect beyond right is invalid`() = assertInvalid(10, 20, 1891, 1040)

    @Test fun `rect beyond top is invalid`() = assertInvalid(10, 19, 1890, 1040)

    @Test fun `rect beyond bottom is invalid`() = assertInvalid(10, 20, 1890, 1041)

    @Test fun `zero width is invalid`() = assertInvalid(10, 20, 10, 40)

    @Test fun `zero height is invalid`() = assertInvalid(10, 20, 40, 20)

    private fun assertValid(left: Int, top: Int, right: Int, bottom: Int) {
        assertTrue(LaunchBoundsSanity.isWithinWorkArea(left, top, right, bottom, workArea))
    }

    private fun assertInvalid(left: Int, top: Int, right: Int, bottom: Int) {
        assertFalse(LaunchBoundsSanity.isWithinWorkArea(left, top, right, bottom, workArea))
    }
}
