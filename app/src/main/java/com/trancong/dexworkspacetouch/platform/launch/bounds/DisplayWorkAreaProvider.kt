package com.trancong.dexworkspacetouch.platform.launch.bounds

interface DisplayWorkAreaProvider {
    fun getSnapshot(): DisplayWorkAreaSnapshot?

    fun getWorkArea(): DisplayWorkArea? = getSnapshot()?.workArea
}
