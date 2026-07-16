package com.trancong.dexworkspacetouch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trancong.dexworkspacetouch.navigation.TouchNavigation
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // DeX-only launch/display gating intentionally waits for the legacy-flow audit.
        enableEdgeToEdge()
        setContent {
            DexWorkspaceTouchTheme {
                TouchNavigation()
            }
        }
    }
}
