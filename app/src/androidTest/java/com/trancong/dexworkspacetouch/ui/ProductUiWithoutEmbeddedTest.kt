package com.trancong.dexworkspacetouch.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.platform.app.InstrumentationRegistry
import com.trancong.dexworkspacetouch.DexWorkspaceTouchApplication
import com.trancong.dexworkspacetouch.MainActivity
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.ProductExecutionValue
import com.trancong.dexworkspacetouch.feature.embeddedworkspace.product.ProductRunPhase
import com.trancong.dexworkspacetouch.navigation.TouchNavigation
import com.trancong.dexworkspacetouch.ui.theme.DexWorkspaceTouchTheme
import com.trancong.dexworkspacetouch.workspace.designer.model.AssignedApp
import com.trancong.dexworkspacetouch.workspace.designer.model.WorkspaceCanvas
import com.trancong.dexworkspacetouch.workspace.execution.embedded.runtime.EmbeddedWorkspaceRunResult
import com.trancong.dexworkspacetouch.workspace.externaltransfer.ExternalTransferViewModel
import com.trancong.dexworkspacetouch.workspace.persistence.domain.Workspace
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import rikka.shizuku.Shizuku

/** Real product composables/navigation below LicenseGate; no license state is modified.
 * Run with an isolated debug applicationId ending in .ui001, never the installed product.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ProductUiWithoutEmbeddedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as DexWorkspaceTouchApplication
    private val displayId get() = InstrumentationRegistry.getArguments().getString("displayId", "0").toInt()

    @Test fun a_homeLibraryAndOrdinaryMenusHaveNoEmbeddedEntry() = withProduct { host ->
        await { texts().any { it == "UI001 Classic" } }
        selectWorkspace("UI001 Classic")
        assertNoEmbeddedEntry("home-library")
        assertNotNull(find("Mở"))
        scrollUntil("UI001 Regular")
        selectWorkspace("UI001 Regular")
        assertNoEmbeddedEntry("regular-selected")
        scrollUntil("UI001 Classic", backwards = true)
        selectWorkspace("UI001 Classic")
        scrollUntil("Tệp", backwards = true)
        click("Tệp")
        await { find("Nút kéo", description = true) != null }
        val handle = checkNotNull(find("Nút kéo", description = true))
        val handleBounds = Rect().also(handle::getBoundsInScreen)
        val sheetWindow = Rect().also(checkNotNull(handle.window)::getBoundsInScreen)
        shellInput("input -d $displayId swipe ${handleBounds.centerX()} ${handleBounds.centerY()} " +
            "${handleBounds.centerX()} ${sheetWindow.top + 40} 300")
        await { find("Giới thiệu") != null }
        assertNoEmbeddedEntry("files-menu")
        click("Giới thiệu")
        await { find("DexWorkspaceTouch") != null }
        assertNoEmbeddedEntry("about-menu")
        shellInput("input -d $displayId keyevent KEYCODE_BACK")
        scrollUntil("Car Mode", backwards = true)
        click("Car Mode")
        await { find("Workspaces") != null }
        scrollUntil("Tự động sửa bố cục Workspace")
        assertNoEmbeddedEntry("car-repair-settings")
        assertNotNull(find("Tắt"))
        assertFalse("New install defaults Auto OFF", app.carWorkspaceShortcutPreferences.autoRepairEnabled.value)
        assertNull(find("Suggest — gợi ý sửa layout"))
        scrollUntil("Workspaces", backwards = true)
        click("Workspaces")
        await { find("Mở") != null }
        assertNoEmbeddedEntry("returned-home")
        if (InstrumentationRegistry.getArguments().getString("smoke", "false").toBoolean()) {
            ensureShizukuPermission()
            click("Mở")
            await { nodes("com.sec.android.app.popupcalculator").isNotEmpty() }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -W --display $displayId -f 0x10020000 -n ${context.packageName}/${MainActivity::class.java.name}"))
                .use { assertTrue("Return to the existing product window", it.readBytes().toString(Charsets.UTF_8).contains("Status: ok")) }
            await { find("Đã gửi yêu cầu mở 1 ứng dụng.") != null }
            capture("classic-open-completed")
            click("Đóng")
            click("Car Mode")
            scrollUntil("Floating Dock")
            click("Hiện Floating Dock", description = true)
            click("Open Car Dock", description = true)
            await { texts().any { it.startsWith("Repair") } }
            capture("floating-dock-repair")
            clickPrefix("Repair")
            await { app.carFloatingDockCoordinator.repairState?.value?.report != null }
            File(File(context.filesDir, "ui001-evidence"), "repair-report.txt")
                .writeText(app.carFloatingDockCoordinator.repairState?.value?.report.toString())
            assertTrue("Dock Repair completes through the real transport",
                checkNotNull(app.carFloatingDockCoordinator.repairState?.value?.report).complete)
            capture("repair-result")
        }
        assertEquals(ProductRunPhase.IDLE, app.embeddedProductRunGate.status.value.phase)
        assertEquals(displayId, host.display?.displayId)
    }

    @Test fun z_cleanupBlockedKeepsSafetyBannerWithoutProductRouteButton() = withProduct {
        val gate = app.embeddedProductRunGate
        val token = checkNotNull(gate.tryAcquireEmbedded("ui001-classic"))
        val operation = checkNotNull(gate.startOperation(token))
        assertTrue(gate.markInvoked(operation))
        assertTrue(gate.acceptResult(operation, ProductExecutionValue.from(
            EmbeddedWorkspaceRunResult.CleanupIncomplete(emptyList(), emptyList()))))
        val before = gate.status.value
        await { find("Chưa xác nhận dọn Embedded") != null }
        assertNull("Home must not navigate to the Embedded product screen", find("Xem trạng thái Embedded"))
        assertFalse(gate.tryDispatchClassic { fail("Classic must remain blocked") })
        assertSame(before, gate.status.value)
        capture("cleanup-blocked-home")
    }

    private fun withProduct(test: (MainActivity) -> Unit) {
        check(context.packageName.endsWith(".ui001")) { "Use the isolated UI001 verification application ID" }
        val info = instrumentation.uiAutomation.serviceInfo
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = info
        val calculator = checkNotNull(context.packageManager.getLaunchIntentForPackage("com.sec.android.app.popupcalculator")?.component)
        val canvas = WorkspaceCanvas.singleCell().let { canvas -> canvas.copy(cells = canvas.cells.map {
            it.copy(app = AssignedApp(calculator.packageName, calculator.className, "Calculator"))
        }) }
        runBlocking {
            for ((id, pinned) in listOf("ui001-classic" to true, "ui001-regular" to false)) {
                if (!app.workspaceRepository.exists(id)) app.workspaceRepository.insert(Workspace(
                    id, if (pinned) "UI001 Classic" else "UI001 Regular", canvas, 1L, 1, 1L, 1L, pinned))
            }
        }
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        val host = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK), options.toBundle()) as MainActivity
        instrumentation.runOnMainSync {
            val transfers = ExternalTransferViewModel()
            host.setContent { DexWorkspaceTouchTheme { TouchNavigation(host, transfers) } }
        }
        assertEquals("Product host display", displayId, host.display?.displayId)
        try { test(host) } catch (failure: Throwable) {
            val directory = File(context.filesDir, "ui001-evidence").apply { mkdirs() }
            File(directory, "failure-nodes.txt").writeText(nodes().joinToString("\n") { it.toString() })
            InstrumentationRegistry.getArguments().getString("screenshotDisplayId")?.toLong()?.let { physicalDisplay ->
                android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                    "screencap -p -d $physicalDisplay")).use { source ->
                    File(directory, "failure.png").outputStream().use { source.copyTo(it) }
                }
            }
            throw failure
        } finally {
            instrumentation.runOnMainSync { app.carFloatingDockCoordinator.hide(); host.finish() }
        }
    }

    private fun nodes(packageName: String = context.packageName, allDisplays: Boolean = false): List<AccessibilityNodeInfo> {
        val all = instrumentation.uiAutomation.windowsOnAllDisplays
        val windows = if (allDisplays) (0 until all.size()).flatMap { all.valueAt(it) }
            else all.get(displayId).orEmpty()
        fun flatten(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
            if (!node.refresh()) return emptyList()
            return listOf(node) + (0 until node.childCount).mapNotNull(node::getChild).flatMap(::flatten)
        }
        return windows.mapNotNull { it.root }.flatMap(::flatten).filter { it.packageName?.toString() == packageName }
    }
    private fun texts() = nodes().flatMap { listOfNotNull(it.text?.toString(), it.contentDescription?.toString()) }
    private fun find(text: String, description: Boolean = false) = nodes().firstOrNull {
        (if (description) it.contentDescription else it.text)?.toString() == text
    }
    private fun click(text: String, description: Boolean = false) {
        await { find(text, description) != null }
        clickNode(checkNotNull(find(text, description)))
    }
    private fun clickPrefix(prefix: String) = clickNode(checkNotNull(nodes().firstOrNull { it.text?.startsWith(prefix) == true }))
    private fun selectWorkspace(name: String) {
        click(name)
        await { find(name)?.let { title -> generateSequence(title) { it.parent }
            .any { it.stateDescription?.contains("Đang được chọn.") == true } } == true }
    }
    private fun clickNode(node: AccessibilityNodeInfo) {
        val action = generateSequence(node) { it.parent }.firstOrNull { it.isClickable && it.isEnabled }
        assertNotNull("Enabled visible action", action)
        val bounds = Rect().also(node::getBoundsInScreen)
        node.window?.let { window -> bounds.intersect(Rect().also(window::getBoundsInScreen)) }
        assertFalse("Visible tap target", bounds.isEmpty)
        val targetDisplay = if (node.packageName?.toString() == context.packageName) displayId
            else node.window?.displayId ?: 0
        shellInput("input -d $targetDisplay tap ${bounds.centerX()} ${bounds.centerY()}")
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
    }
    private fun scrollUntil(text: String, backwards: Boolean = false) {
        repeat(12) {
            assertNoEmbeddedEntry("scroll-$text-$it")
            if (find(text) != null) return
            val direction = if (backwards) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
                else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            val candidates = nodes().filter { node -> node.isScrollable && node.actionList.any { it.id == direction } }
            val scroll = candidates.maxByOrNull { node -> Rect().also(node::getBoundsInScreen).let { it.width() * it.height() } }
            assertNotNull("Vertical product scroll action for $text", scroll)
            val bounds = Rect().also(checkNotNull(scroll)::getBoundsInScreen)
            val upper = bounds.top + bounds.height() / 4
            val lower = bounds.bottom - bounds.height() / 4
            shellInput("input -d $displayId swipe ${bounds.centerX()} ${if (backwards) upper else lower} " +
                "${bounds.centerX()} ${if (backwards) lower else upper} 300")
            SystemClock.sleep(300)
        }
        fail("Product control not found: $text")
    }
    private fun assertNoEmbeddedEntry(label: String) {
        val text = texts()
        capture(label)
        assertFalse("Embedded entry on $label: $text", text.any { it.contains("Embedded", ignoreCase = true) })
        assertFalse("Developer entry on $label", text.any { it.contains("Nhà phát triển") })
    }
    private fun capture(label: String) {
        val directory = File(context.filesDir, "ui001-evidence").apply { mkdirs() }
        File(directory, "${label.replace('/', '-')}.txt").writeText(texts().joinToString("\n"))
    }
    private fun ensureShizukuPermission() {
        await { Shizuku.pingBinder() }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return
        instrumentation.runOnMainSync { Shizuku.requestPermission(41009) }
        val allow = setOf("Allow all the time", "Allow", "Luôn cho phép", "Cho phép", "Cho phép mọi lúc")
        fun permissionNode() = nodes("moe.shizuku.privileged.api", allDisplays = true)
            .firstOrNull { it.text?.toString() in allow }
        await { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED || permissionNode() != null }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) clickNode(checkNotNull(permissionNode()))
        await { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
    }
    private fun shellInput(command: String) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { assertEquals("Native UI input: $command", "", it.readBytes().toString(Charsets.UTF_8).trim()) }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000L
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { "UI observation timed out; evidence is not PASS: ${texts()}" }
            SystemClock.sleep(100)
        }
    }
}
