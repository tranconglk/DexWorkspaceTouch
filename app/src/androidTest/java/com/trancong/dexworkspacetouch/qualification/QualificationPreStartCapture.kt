package com.trancong.dexworkspacetouch.qualification

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject

/** C pre-Start observation only. Does not probe runtime, dispatch clicks or mutate the gate. */
object QualificationPreStartCapture {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var sequence = 0

    fun describeNode(node: AccessibilityNodeInfo): JSONObject {
        val bounds = Rect().also(node::getBoundsInScreen)
        val parents = JSONArray()
        val parentErrors = JSONArray()
        fun readParent(value: AccessibilityNodeInfo): AccessibilityNodeInfo? = runCatching { value.parent }
            .getOrElse { parentErrors.put(it.toString()); null }
        var parent = readParent(node)
        var depth = 0
        while (parent != null && depth++ < 32) {
            parents.put(JSONObject().put("class", parent.className).put("enabled", parent.isEnabled)
                .put("clickable", parent.isClickable).put("visible_to_user", parent.isVisibleToUser))
            if (parent.isClickable) break
            parent = readParent(parent)
        }
        return JSONObject().put("text", node.text ?: JSONObject.NULL)
            .put("content_description", node.contentDescription ?: JSONObject.NULL)
            .put("package", node.packageName ?: JSONObject.NULL).put("class", node.className ?: JSONObject.NULL)
            .put("enabled", node.isEnabled).put("clickable", node.isClickable).put("visible_to_user", node.isVisibleToUser)
            .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
            .put("actions", node.actionList.map { it.id }.let(::JSONArray)).put("parents_to_click_target", parents).put("parent_query_errors", parentErrors)
    }

    fun capture(host: EmbeddedRealProductHarnessActivity, label: String, text: String,
                actionResult: Boolean? = null, dispatchTarget: AccessibilityNodeInfo? = null): JSONObject {
        val runId = requireNotNull(InstrumentationRegistry.getArguments().getString("independentRunId"))
        val directory = instrumentation.targetContext.getExternalFilesDir("dwt-vdm-010-real")!!.resolve(runId)
        directory.mkdirs()
        val number = ++sequence
        val prefix = "prestart-%02d-%s".format(number, label)
        val snapshot = JSONObject().put("category", "C_NEW_INDEPENDENT_PRESTART")
            .put("independent_run_id", runId).put("timestamp_ms", System.currentTimeMillis())
            .put("elapsed_nanos", SystemClock.elapsedRealtimeNanos()).put("app_pid", Process.myPid())
            .put("label", label).put("selector_text", text).put("selector_package", VDM010_HARNESS_PACKAGE)
            .put("action_dispatched", actionResult != null).put("action_click_result", actionResult ?: JSONObject.NULL)
            .put("dispatch_target", dispatchTarget?.let(::describeNode) ?: JSONObject.NULL)
        instrumentation.runOnMainSync {
            val status = host.gate.status.value
            snapshot.put("host_id", host.hostId).put("host_identity", System.identityHashCode(host))
                .put("dex_display_id", host.windowManager.defaultDisplay.displayId)
                .put("activity_class", host.javaClass.name).put("lifecycle_state", host.lifecycle.currentState.name)
                .put("window_has_focus", host.hasWindowFocus()).put("current_focus_class", host.currentFocus?.javaClass?.name ?: JSONObject.NULL)
                .put("route", "EmbeddedWorkspaceProductScreen:vdm010-real")
                .put("gate_phase", status.phase.name).put("generation", status.generation)
                .put("product_token_identity", status.token?.let(System::identityHashCode) ?: JSONObject.NULL)
                .put("start_operation_id", status.startOperationId ?: JSONObject.NULL)
                .put("cleanup_operation_id", status.cleanupOperationId ?: JSONObject.NULL)
                .put("gate_issue", status.issue?.code ?: JSONObject.NULL)
                .put("cleanup_evidence", status.cleanupEvidence.name)
                .put("product_ui_values", QualificationDiagnostics.snapshotUiValues())
                .put("decor_width", host.window.decorView.width).put("decor_height", host.window.decorView.height)
            runCatching {
                val view = host.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                try {
                    view.draw(Canvas(bitmap))
                    directory.resolve("$prefix-host.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    snapshot.put("screenshot", "$prefix-host.png")
                    snapshot.put("screenshot_scope", "own host Canvas decor; native Surface buffers are not captured")
                } finally { bitmap.recycle() }
            }.onFailure { snapshot.put("screenshot_error", it.toString()) }
        }
        val windows = JSONArray()
        val nodes = JSONArray()
        val matches = JSONArray()
        val descriptionOnly = JSONArray()
        val visibleText = JSONArray()
        val candidates = mutableListOf<QualificationClickCandidate>()
        runCatching {
            val automation = instrumentation.uiAutomation
            snapshot.put("accessibility_service_flags", automation.serviceInfo.flags)
                .put("accessibility_service_capabilities", automation.serviceInfo.capabilities)
            val inventory = automation.windowsOnAllDisplays
            for (i in 0 until inventory.size()) {
                val display = inventory.keyAt(i)
                for (window in inventory.valueAt(i)) {
                    val bounds = Rect().also(window::getBoundsInScreen)
                    val root = window.root
                    windows.put(JSONObject().put("display_id", display).put("window_id", window.id)
                        .put("type", window.type).put("title", window.title ?: JSONObject.NULL)
                        .put("active", window.isActive).put("focused", window.isFocused)
                        .put("accessibility_focused", window.isAccessibilityFocused)
                        .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                        .put("root_present", root != null).put("root_package", root?.packageName ?: JSONObject.NULL)
                        .put("root_class", root?.className ?: JSONObject.NULL))
                    if (root == null || root.packageName?.toString() != VDM010_HARNESS_PACKAGE) continue
                    fun visit(node: AccessibilityNodeInfo, path: String) {
                        val value = describeNode(node).put("display_id", display).put("window_id", window.id).put("path", path)
                        nodes.put(value)
                        if (node.isVisibleToUser && (!node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank())) {
                            visibleText.put(value)
                        }
                        if (node.packageName?.toString() == VDM010_HARNESS_PACKAGE && node.text?.toString() == text) {
                            matches.put(value)
                            if (display == host.windowManager.defaultDisplay.displayId) {
                                var target: AccessibilityNodeInfo? = node
                                while (target != null && !target.isClickable) target = target.parent
                                candidates += QualificationClickCandidate(target?.isEnabled)
                            }
                        } else if (node.contentDescription?.toString() == text) descriptionOnly.put(value)
                        for (child in 0 until node.childCount) node.getChild(child)?.let { visit(it, "$path/$child") }
                    }
                    visit(root, "root")
                }
            }
        }.onFailure { snapshot.put("accessibility_capture_error", it.stackTraceToString()) }
        snapshot.put("window_root_inventory", windows).put("own_package_node_inventory", nodes)
            .put("matching_text_nodes_all_displays", matches).put("matching_text_node_count", matches.length())
            .put("description_only_matches", descriptionOnly).put("visible_readiness_error_text_inventory", visibleText)
            .put("selector_outcome", QualificationUiActionEvidence.classify(candidates, actionResult))
        for ((name, command) in mapOf("windows" to "dumpsys window windows", "activities" to "dumpsys activity activities")) {
            runCatching {
                val content = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                    .bufferedReader().use { it.readText() }
                directory.resolve("$prefix-$name.txt").writeText(content)
                val focused = content.lineSequence().filter { line ->
                    listOf("mCurrentFocus", "mFocusedApp", "topResumedActivity", "mResumedActivity", "ResumedActivity")
                        .any(line::contains)
                }.toList()
                snapshot.put("focused_resumed_$name", JSONArray(focused))
            }.onFailure { snapshot.put("dump_" + name + "_error", it.toString()) }
        }
        directory.resolve("$prefix.json").writeText(snapshot.toString(2))
        return snapshot
    }
}
