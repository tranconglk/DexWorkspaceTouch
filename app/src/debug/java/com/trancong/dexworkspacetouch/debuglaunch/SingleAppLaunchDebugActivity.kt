package com.trancong.dexworkspacetouch.debuglaunch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.trancong.dexworkspacetouch.platform.launch.android.ActivityDisplayWorkAreaProvider
import com.trancong.dexworkspacetouch.platform.launch.android.ActivityForegroundLaunchHost
import com.trancong.dexworkspacetouch.platform.launch.android.ActivitySingleAppLaunchPlatform
import com.trancong.dexworkspacetouch.platform.launch.android.AndroidSingleAppLauncher
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.PackageManagerAdapter
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog

class SingleAppLaunchDebugActivity : ComponentActivity() {
    private val referenceViewModel: LegacyReferenceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val packageManagerAdapter = PackageManagerAdapter(packageManager)
        val catalog = DefaultInstalledAppCatalog(
            AndroidInstalledAppDataSource(packageManagerAdapter),
        )
        val launchHost = ActivityForegroundLaunchHost(this)
        val referenceStore = referenceViewModel.store
        val workAreaProvider = ActivityDisplayWorkAreaProvider(
            activity = this,
            legacyReferenceStore = referenceStore,
        )
        setContent {
            DexWorkspaceTouchTheme {
                SingleAppLaunchDebugScreen(
                    catalog = catalog,
                    workAreaProvider = workAreaProvider,
                    launcherFactory = { routingMode ->
                        AndroidSingleAppLauncher(
                            ActivitySingleAppLaunchPlatform(
                                launchHost = launchHost,
                                packageManagerAdapter = packageManagerAdapter,
                                workAreaProviderFactory = { activity ->
                                    ActivityDisplayWorkAreaProvider(
                                        activity = activity,
                                        legacyReferenceStore = referenceStore,
                                    )
                                },
                                displayRoutingMode = routingMode,
                            ),
                        )
                    },
                    hostDisplayIdProvider = {
                        @Suppress("DEPRECATION")
                        windowManager.defaultDisplay.displayId
                    },
                    referenceStore = referenceStore,
                    onFinishHarness = ::finish,
                )
            }
        }
    }
}
