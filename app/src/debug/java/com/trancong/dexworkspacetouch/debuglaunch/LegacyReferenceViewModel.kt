package com.trancong.dexworkspacetouch.debuglaunch

import androidx.lifecycle.ViewModel
import com.trancong.dexworkspacetouch.platform.launch.bounds.LegacyDisplayWorkAreaReferenceStore

class LegacyReferenceViewModel : ViewModel() {
    val store = LegacyDisplayWorkAreaReferenceStore()
}
