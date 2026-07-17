package com.trancong.dexworkspacetouch.platform.launch.android

import kotlinx.coroutines.delay

fun interface LaunchDelay {
    suspend fun wait(milliseconds: Long)
}

internal object CoroutineLaunchDelay : LaunchDelay {
    override suspend fun wait(milliseconds: Long) {
        delay(milliseconds)
    }
}
