package com.trancong.dexworkspacetouch.platform.launch.android

import android.graphics.Rect
import com.trancong.dexworkspacetouch.platform.launch.bounds.PixelBounds

fun PixelBounds.toAndroidRect(): Rect = Rect(left, top, right, bottom)
