package com.trancong.dexworkspacetouch.feature.car.overlay

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.hardware.display.DisplayManager
import android.provider.Settings
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.MainActivity
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.feature.car.CarAction
import com.trancong.dexworkspacetouch.feature.car.CarActionError
import com.trancong.dexworkspacetouch.feature.car.CarActionResult
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutSlot
import com.trancong.dexworkspacetouch.feature.car.CarWorkspaceShortcutStorage
import com.trancong.dexworkspacetouch.feature.car.PreferencesCarWorkspaceShortcutWorkflowProvider
import com.trancong.dexworkspacetouch.feature.car.StoredCarWorkspaceShortcutPreferences
import com.trancong.dexworkspacetouch.feature.car.CarWorkflowExecutionState
import com.trancong.dexworkspacetouch.feature.car.CarActionEngine
import com.trancong.dexworkspacetouch.feature.car.platform.AndroidCarActionExecutor
import com.trancong.dexworkspacetouch.feature.car.platform.RepositoryCarWorkspaceLaunchPlatform
import com.trancong.dexworkspacetouch.platform.launch.android.DisplayTargetWorkspaceLaunchRuntime
import com.trancong.dexworkspacetouch.workspace.apppicker.infrastructure.AndroidInstalledAppDataSource
import com.trancong.dexworkspacetouch.workspace.apppicker.model.DefaultInstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.NormalizedBounds
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCell
import com.trancong.dexworkspacetouch.workspace.launcher.WorkspaceLaunchRequestFactory
import com.trancong.dexworkspacetouch.workspace.launcher.model.LaunchReadiness
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CarOverlaySessionDeviceTest {
    @Test
    fun createMapsServiceFareWorkspace_bindSlot4_andLaunchOnExternalDisplay() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as DexWorkspaceTouchApplication
        assumeTrue(Settings.canDrawOverlays(context))
        val installedAppCatalog = DefaultInstalledAppCatalog(
            AndroidInstalledAppDataSource.create(context),
        )
        val maps = installedAppCatalog.getApps().firstOrNull {
            it.packageName == MapsPackage && it.activityName != null
        }
        val serviceFare = installedAppCatalog.getApps().firstOrNull {
            it.packageName == ServiceFarePackage && it.activityName != null
        }
        assumeTrue(maps != null)
        assumeTrue(serviceFare != null)

        val existing = application.workspaceRepository.getById(DeviceWorkspaceId)
        val allWorkspaces = application.workspaceRepository.observeAll().first()
        val now = System.currentTimeMillis()
        val workspace = Workspace(
            id = DeviceWorkspaceId,
            name = DeviceWorkspaceName,
            canvas = WorkspaceCanvas(
                listOf(
                    WorkspaceCell(
                        id = "maps-left",
                        bounds = NormalizedBounds(0f, 0f, 0.5f, 1f),
                        app = requireNotNull(maps).let {
                            AssignedApp(it.packageName, it.activityName, it.label)
                        },
                    ),
                    WorkspaceCell(
                        id = "service-fare-right",
                        bounds = NormalizedBounds(0.5f, 0f, 1f, 1f),
                        app = requireNotNull(serviceFare).let {
                            AssignedApp(it.packageName, it.activityName, it.label)
                        },
                    ),
                ),
            ),
            modifiedSequence = (allWorkspaces.maxOfOrNull { it.modifiedSequence } ?: 0L) + 1L,
            schemaVersion = 1,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            isPinned = existing?.isPinned ?: false,
        )
        if (existing == null) {
            application.workspaceRepository.insert(workspace)
        } else {
            application.workspaceRepository.update(workspace)
        }
        application.carWorkspaceShortcutPreferences.setWorkspace(
            CarWorkspaceShortcutSlot.Slot4,
            workspace.id,
        )

        val requestFactory = WorkspaceLaunchRequestFactory(installedAppCatalog)
        assertTrue(
            requestFactory.create(workspace.id, workspace.name, workspace.canvas) is
                LaunchReadiness.Ready,
        )
        assertEquals(
            workspace.id,
            application.workspaceRepository.getById(workspace.id)?.id,
        )

        val session = createSession()
        try {
            withExternalMainActivity { activity, displayId ->
                val host = requireNotNull(createActivityCarOverlayHost(activity))
                assertEquals(CarOverlayShowResult.Shown(displayId), onMain { session.show(host) {} })
                val executor = AndroidCarActionExecutor.createForOverlay(
                    context = context,
                    workspaceLaunchPlatform = RepositoryCarWorkspaceLaunchPlatform(
                        application.workspaceRepository,
                        requestFactory,
                        DisplayTargetWorkspaceLaunchRuntime(context) { activity.window.decorView.display },
                    ),
                ) { activity.window.decorView.display }

                assertEquals(
                    CarActionResult.Success,
                    executor.execute(CarAction.Workspace(workspace.id)),
                )
                awaitFocus(displayId, ServiceFarePackage)
                val dump = shell("dumpsys window windows")
                assertTrue(dump.contains(MapsPackage))
                assertTrue(dump.contains(ServiceFarePackage))
                assertTrue(dump.contains("Car floating dock"))
            }
        } finally {
            onMain { session.dispose() }
        }
    }

    @Test
    fun mapsForeground_carTestWorkspaceLaunchesInLayoutAndKeepsOverlay() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as DexWorkspaceTouchApplication
        assumeTrue(Settings.canDrawOverlays(context))
        val requestFactory = WorkspaceLaunchRequestFactory(
            DefaultInstalledAppCatalog(AndroidInstalledAppDataSource.create(context)),
        )
        val workspace = application.workspaceRepository.observeAll().first().firstOrNull {
            it.name == "Car test" &&
                requestFactory.create(it.id, it.name, it.canvas) is LaunchReadiness.Ready
        }
        assumeTrue(workspace != null)
        val session = createSession()
        try {
            withExternalMainActivity { activity, displayId ->
                val host = requireNotNull(createActivityCarOverlayHost(activity))
                val mapsIntent = activity.packageManager
                    .getLaunchIntentForPackage("com.google.android.apps.maps")
                assumeTrue(mapsIntent != null)
                onMain { activity.startActivity(requireNotNull(mapsIntent)) }
                awaitFocus(displayId, "com.google.android.apps.maps")
                val runtime = DisplayTargetWorkspaceLaunchRuntime(context) {
                    activity.window.decorView.display
                }
                val executor = AndroidCarActionExecutor.createForOverlay(
                    context = context,
                    workspaceLaunchPlatform = RepositoryCarWorkspaceLaunchPlatform(
                        application.workspaceRepository,
                        requestFactory,
                        runtime,
                    ),
                ) { activity.window.decorView.display }

                val storage = object : CarWorkspaceShortcutStorage {
                    private val values = mutableMapOf<String, String>()
                    override fun read(key: String) = values[key]
                    override fun write(key: String, value: String?) {
                        if (value == null) values.remove(key) else values[key] = value
                    }
                }
                val preferences = StoredCarWorkspaceShortcutPreferences(storage).also {
                    it.setWorkspace(CarWorkspaceShortcutSlot.Slot1, requireNotNull(workspace).id)
                }
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val coordinator = CarFloatingDockCoordinator(
                    session,
                    PreferencesCarWorkspaceShortcutWorkflowProvider(preferences),
                    CarActionEngine(executor),
                    scope,
                    preferences,
                    application.workspaceRepository,
                    application.carWorkflowExecutionArbiter,
                )
                try {
                    onMain { coordinator.show(host) }
                    assertEquals(CarFloatingDockEdge.Left, session.positionForTest()?.edge)
                    onMain { assertTrue(session.performHandleClickForTest()) }
                    withTimeout(5_000) {
                        while (!onMain {
                                session.performShortcutClickForTest(CarWorkspaceShortcutSlot.Slot1)
                            }) yield()
                    }
                    assertEquals(
                        CarOverlaySessionState.Shown(displayId, CarFloatingDockState.Collapsed),
                        session.state,
                    )
                    assertEquals(
                        CarFloatingDockControlState.Visible(displayId),
                        coordinator.dockState.value,
                    )
                    assertEquals(CarFloatingDockEdge.Left, session.positionForTest()?.edge)
                    withTimeout(15_000) {
                        coordinator.workflowState.first { it is CarWorkflowExecutionState.Idle }
                    }
                    awaitFocus(displayId, "com.google.android.apps.youtube.music")
                    val dump = shell("dumpsys window windows")
                    assertTrue(dump.contains("Car floating dock"))
                    assertTrue(dump.contains("com.google.android.apps.maps"))
                    assertTrue(dump.contains("com.google.android.apps.youtube.music"))
                    onMain { assertTrue(session.performHandleClickForTest()) }
                    assertEquals(
                        CarOverlaySessionState.Shown(displayId, CarFloatingDockState.Expanded),
                        session.state,
                    )
                    onMain {
                        coordinator.hide()
                        coordinator.show(host)
                    }
                    assertEquals(CarFloatingDockEdge.Left, session.positionForTest()?.edge)
                    assertEquals(
                        CarOverlaySessionState.Shown(displayId, CarFloatingDockState.Collapsed),
                        session.state,
                    )
                } finally {
                    onMain { coordinator.dispose() }
                    scope.cancel()
                }
            }
        } finally {
            onMain { session.dispose() }
        }
    }

    @Test
    fun missingWorkspaceAndStaleDisplayFailWithoutPhoneFallback() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as DexWorkspaceTouchApplication
        val requestFactory = WorkspaceLaunchRequestFactory(
            DefaultInstalledAppCatalog(AndroidInstalledAppDataSource.create(context)),
        )
        val workspacePlatform = RepositoryCarWorkspaceLaunchPlatform(
            application.workspaceRepository,
            requestFactory,
            DisplayTargetWorkspaceLaunchRuntime(context) { null },
        )
        val executor = AndroidCarActionExecutor.createForOverlay(
            context = context,
            workspaceLaunchPlatform = workspacePlatform,
        ) { null }
        assertEquals(
            CarActionResult.Failure(CarActionError.WorkspaceUnavailable),
            executor.execute(CarAction.Workspace("workspace-that-does-not-exist")),
        )
        val readyWorkspace = application.workspaceRepository.observeAll().first().firstOrNull {
            requestFactory.create(it.id, it.name, it.canvas) is LaunchReadiness.Ready
        }
        assumeTrue(readyWorkspace != null)
        assertTrue(
            executor.execute(CarAction.Workspace(requireNotNull(readyWorkspace).id)) is
                CarActionResult.Failure,
        )
        val defaultDisplay = Regex(
            "Display: mDisplayId=0[\\s\\S]*?(?=Display: mDisplayId=|$)",
        ).find(shell("dumpsys window displays"))?.value.orEmpty()
        assertTrue(!defaultDisplay.contains("mCurrentFocus=com.google.android.apps.maps"))
        assertTrue(!defaultDisplay.contains("mCurrentFocus=com.google.android.apps.youtube.music"))
    }

    @Test
    fun externalOverlaySurvivesActivityRecreationAndBackground() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(Settings.canDrawOverlays(context))
        val session = createSession()
        var oldActivityReference: WeakReference<Activity>? = null

        withExternalMainActivity { activity, displayId ->
            oldActivityReference = WeakReference(activity)
            val host = requireNotNull(createActivityCarOverlayHost(activity))
            assertEquals(CarOverlayShowResult.Shown(displayId), onMain { session.show(host) {} })
            onMain { activity.recreate() }
            assertEquals(
                CarOverlaySessionState.Shown(displayId, CarFloatingDockState.Collapsed),
                session.state,
            )
        }

        // The session owns only application/display/window contexts, never this Activity.
        assertTrue(oldActivityReference != null)
        onMain { session.dispose() }
        onMain { session.dispose() }
        assertEquals(CarOverlaySessionState.Disposed, session.state)
        Unit
    }

    @Test
    fun mapsForeground_expandedDockEmitsActionsAndCollapses() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(Settings.canDrawOverlays(context))
        val session = createSession()
        withExternalMainActivity { activity, displayId ->
            val actionLatch = CountDownLatch(6)
            val receivedActions = java.util.Collections.synchronizedList(
                mutableListOf<CarWorkspaceShortcutSlot>(),
            )
            assertEquals(
                CarOverlayShowResult.Shown(displayId),
                onMain {
                    session.show(requireNotNull(createActivityCarOverlayHost(activity))) {
                        receivedActions += it
                        actionLatch.countDown()
                    }
                    session.updateShortcuts(
                        CarWorkspaceShortcutSlot.entries.take(6).map { slot ->
                            CarFloatingWorkspaceShortcut(
                                slot = slot,
                                accessibilityLabel = "Workspace ${slot.ordinal + 1}",
                                state = CarFloatingWorkspaceShortcutState.Configured(
                                    "workspace-${slot.ordinal + 1}",
                                ),
                                preview = CarFloatingWorkspacePreview(emptyList()),
                            )
                        },
                    )
                },
            )
            val mapsIntent = activity.packageManager
                .getLaunchIntentForPackage("com.google.android.apps.maps")
            assumeTrue(mapsIntent != null)
            onMain { activity.startActivity(requireNotNull(mapsIntent)) }
            assertTrue("Six real Dock slot taps were not received", actionLatch.await(60, TimeUnit.SECONDS))
            assertEquals(
                CarWorkspaceShortcutSlot.entries.take(6),
                receivedActions.toList(),
            )
            val collapseDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (
                session.state !is CarOverlaySessionState.Shown ||
                (session.state as CarOverlaySessionState.Shown).dockState != CarFloatingDockState.Collapsed
            ) {
                if (System.nanoTime() >= collapseDeadline) break
                Thread.sleep(50)
            }
            assertEquals(
                CarOverlaySessionState.Shown(displayId, CarFloatingDockState.Collapsed),
                session.state,
            )
            onMain { session.hide() }
            assertEquals(CarOverlaySessionState.NotShown, session.state)
        }
        Unit
    }

    private fun createSession(): CarOverlaySession {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return CarOverlaySession(
            AndroidCarOverlayPermissionChecker(context),
            AndroidCarFloatingDockWindowFactory(context),
            AndroidCarOverlayDisplayEvents(context),
        )
    }

    private fun awaitFocus(displayId: Int, packageName: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        do {
            val dump = shell("dumpsys window displays")
            val displaySection = Regex(
                "Display: mDisplayId=$displayId[\\s\\S]*?(?=Display: mDisplayId=|$)",
            ).find(dump)?.value.orEmpty()
            if (displaySection.contains("mCurrentFocus=") && displaySection.contains(packageName)) return
            Thread.sleep(100)
        } while (System.nanoTime() < deadline)
        throw AssertionError("$packageName did not become focused on display $displayId")
    }

    private fun shell(command: String): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(command)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)
            .bufferedReader()
            .use { it.readText() }
    }

    private suspend fun withExternalMainActivity(block: suspend (Activity, Int) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val androidDisplay = targetContext.getSystemService(DisplayManager::class.java)
            .displays
            .firstOrNull {
                it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
            }
        assumeTrue(androidDisplay != null)
        val externalDisplay = requireNotNull(androidDisplay)
        val intent = Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(externalDisplay.displayId).toBundle()
        val activity = instrumentation.startActivitySync(intent, options)
        try {
            block(activity, externalDisplay.displayId)
        } finally {
            onMain { activity.finish() }
        }
    }

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<Result<T>>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result.set(runCatching(block))
        }
        return requireNotNull(result.get()).getOrThrow()
    }

    private companion object {
        const val DeviceWorkspaceId = "device-car-test-maps-servicefare-v1"
        const val DeviceWorkspaceName = "Car test Maps + ServiceFare"
        const val MapsPackage = "com.google.android.apps.maps"
        const val ServiceFarePackage = "com.trancong.servicefare"
    }
}
