package com.trancong.dexworkspacetouch.feature.embeddedapp.remote

import android.app.ActivityManager
import android.content.AttributionSource
import android.hardware.display.VirtualDisplayConfig
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTarget
import com.trancong.dexworkspacetouch.feature.embeddedapp.EmbeddedAppTaskCandidate
import com.trancong.dexworkspacetouch.feature.embeddedapp.CleanupProgress
import com.trancong.dexworkspacetouch.feature.embeddedapp.CleanupStep
import com.trancong.dexworkspacetouch.feature.embeddedapp.selectNewTargetTask
import com.trancong.dexworkspacetouch.feature.embeddedapp.selectRecordedTargetTask
import com.trancong.dexworkspacetouch.feature.embeddedapp.dispatcherReady
import com.trancong.dexworkspacetouch.feature.embeddedapp.virtualToolType
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** Disposable TVL-005F2C2 path: exactly one VirtualDevice and one owned display. */
@android.annotation.SuppressLint("PrivateApi", "NewApi", "WrongConstant")
object EmbeddedAppVdm {
    private const val TAG = "TaskViewLab.VDMSurface"
    private const val TRUSTED_DISPLAY_FLAG = 0x400
    private var device: Any? = null
    private var displayCallback: Any? = null
    private var receivedSurface: Surface? = null
    private var displayId = -1
    private var deviceId = -1
    private var beforeTaskIds = emptySet<Int>()
    private var activeTarget: EmbeddedAppTarget? = null
    private var activeTaskId = -1
    private var touchToken: IBinder? = null
    private var returnedInput: Any? = null
    private var inputDeviceId = -1
    private var touchClosed = false
    private var cleanupProgress = CleanupProgress()

    private fun type(name: String) = Class.forName(name)

    private fun inertListener(name: String): Any {
        val callbackType = type(name)
        val binder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                if (code == IBinder.INTERFACE_TRANSACTION) {
                    reply?.writeString(name)
                    return true
                }
                data.enforceInterface(name)
                Log.i(TAG, "callback=$name code=$code")
                return true
            }
        }
        lateinit var proxy: Any
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "asBinder" -> binder
                "toString" -> "$name inert"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> null
            }
        }
        proxy = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType), handler)
        binder.attachInterface(proxy as IInterface, name)
        return proxy
    }

    private fun manager(): Any {
        val service = type("android.os.ServiceManager").getMethod("getService", String::class.java)
            .invoke(null, "virtualdevice") as IBinder
        val managerType = type("android.companion.virtual.IVirtualDeviceManager")
        return type("${managerType.name}\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, service)!!
    }

    @Synchronized
    fun create(surface: Surface, target: EmbeddedAppTarget, associationId: Int): Bundle {
        val identity = Binder.clearCallingIdentity()
        try {
            check(Process.myUid() == 2000) { "Requires shell UID 2000" }
            check(device == null) { "VDM Surface probe already active" }
            check(activeTarget == null) { "Previous VDM session references were not released" }
            check(surface.isValid) { "Received Surface is invalid" }
            val (width, height, densityDpi) = target.geometry
            activeTarget = target
            cleanupProgress = CleanupProgress()
            require(associationId > 0) { "Association ID must be positive" }
            receivedSurface = surface
            beforeTaskIds = runningTasks().map { it.taskId }.toSet()

            val attribution = AttributionSource.Builder(Process.myUid())
                .setPackageName("com.android.shell").build()
            val paramsType = type("android.companion.virtual.VirtualDeviceParams")
            val paramsBuilderType = type("${paramsType.name}\$Builder")
            val params = paramsBuilderType.getMethod("build")
                .invoke(paramsBuilderType.getConstructor().newInstance())
            val activityName = "android.companion.virtual.IVirtualDeviceActivityListener"
            val soundName = "android.companion.virtual.IVirtualDeviceSoundEffectListener"
            val managerType = type("android.companion.virtual.IVirtualDeviceManager")
            Log.i(TAG, "createVirtualDevice begin uid=${Process.myUid()} associationId=$associationId surfaceValid=${surface.isValid}")
            val created = managerType.getMethod("createVirtualDevice", IBinder::class.java,
                AttributionSource::class.java, Int::class.javaPrimitiveType, paramsType,
                type(activityName), type(soundName)).invoke(manager(), Binder(), attribution,
                associationId, params, inertListener(activityName), inertListener(soundName))
            device = created
            val deviceType = type("android.companion.virtual.IVirtualDevice")
            deviceId = deviceType.getMethod("getDeviceId").invoke(created) as Int

            val config = VirtualDisplayConfig.Builder("TVL-VDM-Trusted", width, height, densityDpi)
                .setSurface(surface).setFlags(TRUSTED_DISPLAY_FLAG).build()
            val callbackName = "android.hardware.display.IVirtualDisplayCallback"
            val callback = inertListener(callbackName)
            displayCallback = callback
            Log.i(TAG, "createVirtualDisplay begin deviceId=$deviceId config=$config surfaceValid=${surface.isValid}")
            displayId = deviceType.getMethod("createVirtualDisplay", VirtualDisplayConfig::class.java,
                type(callbackName)).invoke(created, config, callback) as Int
            check(displayId > 0) { "Invalid displayId=$displayId" }
            Log.i(TAG, "create success deviceId=$deviceId displayId=$displayId remoteSurfaceValid=${surface.isValid}")
            return result(true).apply {
                putInt("deviceId", deviceId)
                putInt("displayId", displayId)
                putInt("requestedFlags", TRUSTED_DISPLAY_FLAG)
                putBoolean("surfaceValid", surface.isValid)
            }
        } catch (error: Throwable) {
            val cause = unwrap(error)
            Log.e(TAG, "create failed", cause)
            runCatching { closeDevice() }
            return failure(cause)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @Synchronized
    fun launchTarget(): Bundle {
        check(device != null && displayId > 0) { "Gate A display is not active" }
        check(receivedSurface?.isValid == true) { "Received Surface became invalid" }
        val target = checkNotNull(activeTarget) { "Embedded target is missing" }
        Log.i(TAG, "target launch begin component=${target.componentName} displayId=$displayId surfaceValid=${receivedSurface?.isValid}")
        return ShellDisplayLaunch.launch(displayId, target).also {
            Log.i(TAG, "target launch result=${it.getInt("result")} success=${it.getBoolean("success")}")
            if (it.getBoolean("success")) activeTaskId = awaitLaunchedTargetTask(target).taskId
        }
    }

    private fun awaitLaunchedTargetTask(target: EmbeddedAppTarget): EmbeddedAppTaskCandidate {
        val deadline = SystemClock.uptimeMillis() + 3_000
        do {
            val candidate = selectNewTargetTask(taskCandidates(), beforeTaskIds, target, displayId)
            if (candidate != null) return candidate
            Thread.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Explicit target task did not appear for ${target.componentName} on display $displayId")
    }

    @Synchronized
    fun prepareTouchscreen(): Bundle {
        val identity = Binder.clearCallingIdentity()
        try {
            check(Process.myUid() == 2000) { "Requires shell UID 2000" }
            val activeDevice = checkNotNull(device) { "Gate A VirtualDevice is not active" }
            check(displayId > 0 && receivedSurface?.isValid == true) { "Gate A display is not active" }
            check(touchToken == null) { "Virtual touchscreen creation already attempted" }
            check(runningTasks().none { taskDisplayId(it) == displayId }) {
                "Display $displayId must be empty before touchscreen registration"
            }

            val token = Binder()
            touchToken = token
            touchClosed = false
            val configType = type("android.hardware.input.VirtualTouchscreenConfig")
            val builderType = type("android.hardware.input.VirtualTouchscreenConfig\$Builder")
            val builder = builderType.getConstructor(Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType).newInstance(checkNotNull(activeTarget).geometry.width, checkNotNull(activeTarget).geometry.height)
            builderType.getMethod("setInputDeviceName", String::class.java)
                .invoke(builder, TOUCH_DEVICE_NAME)
            builderType.getMethod("setVendorId", Int::class.javaPrimitiveType).invoke(builder, 0)
            builderType.getMethod("setProductId", Int::class.javaPrimitiveType).invoke(builder, 0)
            builderType.getMethod("setAssociatedDisplayId", Int::class.javaPrimitiveType)
                .invoke(builder, displayId)
            val config = builderType.getMethod("build").invoke(builder)
            val deviceType = type("android.companion.virtual.IVirtualDevice")
            Log.i(TAG, "TVL005F2F1 create touchscreen begin token=$TOUCH_TOKEN_LABEL displayId=$displayId config=${activeTarget?.geometry?.width}x${activeTarget?.geometry?.height} vendor=0 product=0")
            val createdInput = checkNotNull(deviceType
                .getMethod("createVirtualTouchscreen", configType, IBinder::class.java)
                .invoke(activeDevice, config, token)) {
                "createVirtualTouchscreen returned null IVirtualInputDevice"
            }
            returnedInput = createdInput
            val inputType = type("android.hardware.input.IVirtualInputDevice")
            val associatedDisplayId = inputType.getMethod("getAssociatedDisplayId")
                .invoke(createdInput) as Int
            check(associatedDisplayId == displayId) {
                "Returned input display mismatch expected=$displayId actual=$associatedDisplayId"
            }
            inputDeviceId = inputType.getMethod("getInputDeviceId").invoke(createdInput) as Int
            check(inputDeviceId >= 0) { "Invalid InputDevice ID=$inputDeviceId" }
            val input = inputDevice(inputDeviceId)
            checkNotNull(input) { "InputDevice $inputDeviceId not registered" }
            val descriptorDisplayId = input.javaClass.getMethod("getAssociatedDisplayId").invoke(input) as Int
            check(descriptorDisplayId == displayId) {
                "InputDevice display mismatch expected=$displayId actual=$descriptorDisplayId"
            }
            check(input.name == TOUCH_DEVICE_NAME) {
                "InputDevice name mismatch expected=$TOUCH_DEVICE_NAME actual=${input.name}"
            }
            Log.i(TAG, "TVL006A2 touchscreen registered returnedInput=true inputDeviceId=$inputDeviceId " +
                "associatedDisplayId=$associatedDisplayId descriptorDisplayId=$descriptorDisplayId name=${input.name}")

            return result(true).apply {
                putInt("displayId", displayId)
                putInt("inputDeviceId", inputDeviceId)
                putInt("associatedDisplayId", associatedDisplayId)
                putInt("descriptorDisplayId", descriptorDisplayId)
            }
        } catch (error: Throwable) {
            val cause = unwrap(error)
            Log.e(TAG, "TVL005F2F1 touchscreen preparation failed", cause)
            return failure(cause)
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
    }

    @Synchronized
    fun sendDirectTouch(action: Int, x: Float, y: Float, pressure: Float, eventTimeNanos: Long): Bundle {
        val identity = Binder.clearCallingIdentity()
        return try {
            check(Process.myUid() == 2000)
            val geometry = checkNotNull(activeTarget).geometry
            require(action in 0..3 && x in 0f..geometry.width.toFloat() && y in 0f..geometry.height.toFloat())
            val input = checkNotNull(returnedInput) { "Virtual touchscreen is not active" }
            val builderType = type("android.hardware.input.VirtualTouchEvent\$Builder")
            val builder = builderType.getConstructor().newInstance()
            builderType.getMethod("setPointerId", Int::class.javaPrimitiveType).invoke(builder, 0)
            builderType.getMethod("setToolType", Int::class.javaPrimitiveType)
                .invoke(builder, virtualToolType(action))
            builderType.getMethod("setAction", Int::class.javaPrimitiveType).invoke(builder, action)
            builderType.getMethod("setX", Float::class.javaPrimitiveType).invoke(builder, x)
            builderType.getMethod("setY", Float::class.javaPrimitiveType).invoke(builder, y)
            if (action == 0 || pressure > 0f) builderType.getMethod("setPressure", Float::class.javaPrimitiveType).invoke(builder, pressure)
            builderType.getMethod("setEventTimeNanos", Long::class.javaPrimitiveType).invoke(builder, eventTimeNanos)
            val event = builderType.getMethod("build").invoke(builder)
            val sent = type("android.hardware.input.IVirtualInputDevice").getMethod("sendTouchEvent", type("android.hardware.input.VirtualTouchEvent")).invoke(input, event) as Boolean
            result(sent).apply { putBoolean("sent", sent); putInt("displayId", displayId) }
        } catch (error: Throwable) { failure(unwrap(error)) }
        finally { Binder.restoreCallingIdentity(identity) }
    }
    @Synchronized
    fun cleanup(): Bundle {
        val identity = Binder.clearCallingIdentity()
        var removedTaskId = -1
        var error: Throwable? = null
        try {
            fun attempt(step: CleanupStep, label: String, action: () -> Unit) {
                if (cleanupProgress.next() != step) return
                try { action() }
                catch (failure: Throwable) {
                    val cause = unwrap(failure)
                    if (error == null) error = cause
                    Log.e(TAG, "$label failed", cause)
                } finally { cleanupProgress.complete(step) }
            }
            attempt(CleanupStep.INPUT, "touchscreen unregister") { closeTouchscreen() }
            attempt(CleanupStep.TASK, "target cleanup") {
                if (displayId > 0) {
                    val target = checkNotNull(activeTarget) { "Embedded target is missing" }
                    val tasks = runningTasks()
                    val candidate = selectRecordedTargetTask(taskCandidates(tasks), activeTaskId,
                        target, displayId)
                    candidate?.let { selected ->
                        check(removeTask(selected.taskId)) {
                            "removeTask(${selected.taskId}) returned false"
                        }
                        removedTaskId = selected.taskId
                        Log.i(TAG, "target removeTask success taskId=$removedTaskId displayId=$displayId")
                    }
                }
            }
            attempt(CleanupStep.DEVICE, "VirtualDevice close") { closeDevice() }
        } finally {
            Binder.restoreCallingIdentity(identity)
        }
        return if (error == null) result(true).apply { putInt("removedTaskId", removedTaskId) }
        else failure(checkNotNull(error)).apply { putInt("removedTaskId", removedTaskId) }
    }

    @Synchronized
    fun finishCleanupAfterAssociation() {
        if (cleanupProgress.next() == CleanupStep.ASSOCIATION) {
            cleanupProgress.complete(CleanupStep.ASSOCIATION)
        }
        if (cleanupProgress.next() == CleanupStep.REFERENCES) {
            releaseReferences()
            cleanupProgress.complete(CleanupStep.REFERENCES)
        }
    }

    private fun closeDevice() {
        val closing = device
        device = null
        closing?.let {
            Log.i(TAG, "IVirtualDevice.close begin deviceId=$deviceId displayId=$displayId")
            type("android.companion.virtual.IVirtualDevice").getMethod("close").invoke(it)
            Log.i(TAG, "IVirtualDevice.close success")
        }
    }

    private fun releaseReferences() {
        displayCallback = null
        receivedSurface?.release()
        receivedSurface = null
        displayId = -1
        deviceId = -1
        beforeTaskIds = emptySet()
        activeTarget = null
        activeTaskId = -1
        touchToken = null
        returnedInput = null
        inputDeviceId = -1
        touchClosed = false
    }

    private fun closeTouchscreen() {
        if (touchToken == null && returnedInput == null) return
        check(!touchClosed) { "Touchscreen already closed" }
        val input = checkNotNull(returnedInput) { "Returned IVirtualInputDevice missing at cleanup" }
        val closingInputDeviceId = inputDeviceId
        touchClosed = true
        touchToken = null
        returnedInput = null
        inputDeviceId = -1
        Log.i(TAG, "TVL006A2 IVirtualInputDevice.close begin inputDeviceId=$closingInputDeviceId")
        type("android.hardware.input.IVirtualInputDevice").getMethod("close").invoke(input)
        val deadline = SystemClock.uptimeMillis() + 2_000
        while (inputDevice(closingInputDeviceId) != null && SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(25)
        }
        val removed = inputDevice(closingInputDeviceId) == null
        check(removed) { "InputDevice $closingInputDeviceId still registered after close" }
        val activeDevice = checkNotNull(device) { "VirtualDevice closed with touchscreen" }
        val liveDeviceId = type("android.companion.virtual.IVirtualDevice")
            .getMethod("getDeviceId").invoke(activeDevice) as Int
        check(liveDeviceId == deviceId && receivedSurface?.isValid == true) {
            "VirtualDevice did not survive touchscreen close expected=$deviceId actual=$liveDeviceId"
        }
        Log.i(TAG, "TVL006A2 IVirtualInputDevice.close success count=1 descriptorRemoved=true " +
            "virtualDeviceAlive=true deviceId=$liveDeviceId displayId=$displayId")
    }

    private fun inputDevice(id: Int): android.view.InputDevice? {
        if (id < 0) return null
        val global = type("android.hardware.input.InputManagerGlobal").getMethod("getInstance").invoke(null)
        return type("android.hardware.input.InputManagerGlobal")
            .getMethod("getInputDevice", Int::class.javaPrimitiveType).invoke(global, id) as android.view.InputDevice?
    }

    private data class DispatcherReadiness(val ready: Boolean, val detail: String)

    private fun waitForDispatcherReady(expectedDisplayId: Int, x: Int, y: Int,
        expectedPackage: String, expectedClass: String): DispatcherReadiness {
        val deadline = SystemClock.uptimeMillis() + 3_000
        var last = "not sampled"
        do {
            val input = shellDump("input")
            val display = shellDump("display")
            val windows = shellDump("window", "displays")
            val focusedDisplay = Regex("FocusedDisplayId:\\s*(\\d+)").find(input)?.groupValues?.get(1)?.toIntOrNull()
            val focusedWindow = input.substringAfter("FocusedWindows:")
                .substringBefore("FocusRequests:").lineSequence().any {
                    it.contains("displayId=$expectedDisplayId") && it.contains(expectedPackage) &&
                        it.contains(expectedClass)
                }
            val targetLine = input.lineSequence().firstOrNull {
                it.contains("displayId=$expectedDisplayId") && it.contains(expectedPackage) &&
                    it.contains(expectedClass) &&
                    !it.contains("ActivityRecordInputSink") && it.contains("touchableRegion=")
            }
            val touchable = targetLine != null && !targetLine.contains("NOT_TOUCHABLE") &&
                regionContains(targetLine, x, y)
            val displaySection = display.substringAfter("Display $expectedDisplayId:", "")
                .substringBefore("Display ${expectedDisplayId + 1}:")
            val transitionIdle = displaySection.contains("mIsInTransition=false")
            val windowSection = windows.substringAfter("Display: mDisplayId=$expectedDisplayId", "")
                .substringBefore("Display: mDisplayId=")
            val layoutIdle = windowSection.contains("mLayoutNeeded=false")
            val target = activeTarget
            val targetRunning = target != null && runningTasks().any {
                taskDisplayId(it) == expectedDisplayId && it.isVisible &&
                    it.topActivity?.packageName == target.packageName &&
                    it.topActivity?.className == target.componentName
            }
            last = "focusedDisplay=$focusedDisplay focusedWindow=$focusedWindow targetWindow=${targetLine != null} " +
                "touchable=$touchable transitionIdle=$transitionIdle layoutIdle=$layoutIdle resumedVisible=$targetRunning"
            if (dispatcherReady(focusedWindow, targetLine != null, touchable, transitionIdle,
                    layoutIdle, targetRunning, descriptorMatchesDisplay = true)) {
                return DispatcherReadiness(true, last)
            }
            Thread.sleep(25)
        } while (SystemClock.uptimeMillis() < deadline)
        return DispatcherReadiness(false, last)
    }

    private fun regionContains(line: String, x: Int, y: Int): Boolean {
        val match = Regex("touchableRegion=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]")
            .find(line) ?: return false
        val (left, top, right, bottom) = match.destructured.toList().map(String::toInt)
        return x >= left && x < right && y >= top && y < bottom
    }

    private fun shellDump(vararg args: String): String {
        val process = ProcessBuilder(listOf("dumpsys") + args).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "dumpsys ${args.joinToString(" ")} failed: $text" }
        return text
    }

    @Suppress("UNCHECKED_CAST")
    private fun runningTasks(): List<ActivityManager.RunningTaskInfo> {
        val manager = type("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        return type("android.app.IActivityTaskManager").getMethod("getTasks",
            Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(manager, 1000, false, true, -1) as List<ActivityManager.RunningTaskInfo>
    }

    private fun taskDisplayId(task: ActivityManager.RunningTaskInfo): Int =
        task.javaClass.getField("displayId").getInt(task)

    private fun taskCandidates(tasks: List<ActivityManager.RunningTaskInfo> = runningTasks()) =
        tasks.map {
            EmbeddedAppTaskCandidate(it.taskId, taskDisplayId(it),
                it.baseActivity?.packageName ?: it.topActivity?.packageName,
                it.topActivity?.className ?: it.baseActivity?.className)
        }

    private fun removeTask(taskId: Int): Boolean {
        val manager = type("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
        return type("android.app.IActivityTaskManager").getMethod("removeTask",
            Int::class.javaPrimitiveType).invoke(manager, taskId) as Boolean
    }

    private fun result(success: Boolean) = Bundle().apply {
        putInt("remoteUid", Process.myUid())
        putBoolean("success", success)
    }
    private fun failure(error: Throwable) = result(false).apply {
        putString("exception", "${error.javaClass.name}: ${error.message}")
    }
    private fun unwrap(error: Throwable): Throwable {
        var current = error
        while (current is InvocationTargetException && current.targetException != null) {
            current = current.targetException
        }
        return current
    }

    private const val TOUCH_DEVICE_NAME = "TVL Virtual Touchscreen"
    private const val TOUCH_TOKEN_LABEL = "TVL Virtual Touchscreen token"
}
