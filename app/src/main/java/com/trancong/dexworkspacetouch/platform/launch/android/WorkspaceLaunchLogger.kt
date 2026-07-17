package com.trancong.dexworkspacetouch.platform.launch.android

import android.util.Log

fun interface WorkspaceLaunchLogger {
    fun log(message: String)

    companion object {
        val Android = WorkspaceLaunchLogger { message -> Log.d(LOG_TAG, message) }
        val None = WorkspaceLaunchLogger { }

        private const val LOG_TAG = "WorkspaceLauncher"
    }
}
