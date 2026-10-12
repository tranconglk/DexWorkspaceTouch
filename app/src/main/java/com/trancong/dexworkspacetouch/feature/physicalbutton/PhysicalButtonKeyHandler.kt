package com.trancong.dexworkspacetouch.feature.physicalbutton

data class PhysicalButtonKeyEvent(
    val keyCode: Int,
    val action: Int,
    val repeatCount: Int,
    val deviceId: Int,
    val downTime: Long,
    val isExternal: Boolean,
    val deviceDescriptor: String? = null,
    val isVirtual: Boolean = false,
    val eventTime: Long = downTime,
)

enum class PhysicalButtonKeyDecision { PASS_THROUGH, CONSUME, BIND_REMOTE, TRACK_PRESS, SHORT_PRESS, LONG_PRESS }
enum class PhysicalButtonGesture { SHORT_PRESS, LONG_PRESS }

class PhysicalButtonKeyHandler(val longPressThresholdMs: Long = 800L) {
    private data class ConsumedPress(
        val downTime: Long,
        val descriptor: String,
        val configuration: PhysicalButtonConfiguration?,
        var gestureEmitted: Boolean = false,
        var invalidated: Boolean = false,
    )
    private data class PressKey(val deviceId: Int, val keyCode: Int)
    // deviceId chỉ ghép DOWN/UP của lần nhấn hiện tại, không dùng làm binding identity.
    private val consumedPresses = mutableMapOf<PressKey, ConsumedPress>()

    fun handle(
        event: PhysicalButtonKeyEvent,
        configuration: PhysicalButtonConfiguration,
        availableWorkspaceIds: Set<String>,
        bindingRequested: Boolean = false,
    ): PhysicalButtonKeyDecision {
        if ((event.keyCode != VOLUME_UP && event.keyCode != VOLUME_DOWN) || !event.isExternal || event.isVirtual) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        val descriptor = event.deviceDescriptor?.takeIf(String::isNotBlank)
            ?: return PhysicalButtonKeyDecision.PASS_THROUGH

        // Kết thúc cả chuỗi đã nhận, kể cả khi cấu hình đổi trong lúc giữ nút.
        val pressKey = PressKey(event.deviceId, event.keyCode)
        val press = consumedPresses[pressKey]?.takeIf {
            it.downTime == event.downTime && it.descriptor == descriptor
        }
        if (press != null) {
            invalidate(pressKey, press, configuration, availableWorkspaceIds, bindingRequested)
            when (event.action) {
                ACTION_DOWN -> return gesture(press, event.eventTime, released = false)
                ACTION_UP -> {
                    consumedPresses.remove(pressKey)
                    return gesture(press, event.eventTime, released = true)
                }
            }
        }
        if (event.action != ACTION_DOWN || event.repeatCount != 0) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        if (bindingRequested) {
            if (event.keyCode != VOLUME_UP) return PhysicalButtonKeyDecision.PASS_THROUGH
            consumedPresses[pressKey] = ConsumedPress(event.downTime, descriptor, configuration = null)
            return PhysicalButtonKeyDecision.BIND_REMOTE
        }
        if (!configuration.enabled || configuration.deviceDescriptor.isNullOrBlank() ||
            descriptor != configuration.deviceDescriptor ||
            configuration.workspaceForKey(event.keyCode) !in availableWorkspaceIds) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        consumedPresses[pressKey] = ConsumedPress(event.downTime, descriptor, configuration)
        return PhysicalButtonKeyDecision.TRACK_PRESS
    }

    fun handle(
        event: PhysicalButtonKeyEvent,
        enabled: Boolean,
        configuredDeviceDescriptor: String? = null,
        bindingRequested: Boolean = false,
    ): PhysicalButtonKeyDecision = handle(
        event,
        PhysicalButtonConfiguration(enabled, "mapped", "mapped", configuredDeviceDescriptor),
        setOf("mapped"),
        bindingRequested,
    )

    fun handleLongPress(
        event: PhysicalButtonKeyEvent,
        configuration: PhysicalButtonConfiguration,
        availableWorkspaceIds: Set<String>,
        bindingRequested: Boolean,
        nowMillis: Long,
    ): PhysicalButtonKeyDecision {
        val key = PressKey(event.deviceId, event.keyCode)
        val press = consumedPresses[key]?.takeIf {
            it.downTime == event.downTime && it.descriptor == event.deviceDescriptor
        } ?: return PhysicalButtonKeyDecision.PASS_THROUGH
        invalidate(key, press, configuration, availableWorkspaceIds, bindingRequested)
        return gesture(press, nowMillis, released = false)
    }

    fun invalidatePresses(
        configuration: PhysicalButtonConfiguration,
        availableWorkspaceIds: Set<String>,
        bindingRequested: Boolean,
    ) {
        consumedPresses.forEach { (key, press) ->
            invalidate(key, press, configuration, availableWorkspaceIds, bindingRequested)
        }
    }

    fun clear() { consumedPresses.clear() }

    private fun invalidate(
        key: PressKey,
        press: ConsumedPress,
        configuration: PhysicalButtonConfiguration,
        availableWorkspaceIds: Set<String>,
        bindingRequested: Boolean,
    ) {
        if (press.configuration != null && (configuration != press.configuration || bindingRequested ||
                configuration.workspaceForKey(key.keyCode) !in availableWorkspaceIds)) {
            press.invalidated = true
        }
    }

    private fun gesture(press: ConsumedPress, eventTime: Long, released: Boolean): PhysicalButtonKeyDecision {
        if (press.configuration == null || press.invalidated || press.gestureEmitted) {
            return PhysicalButtonKeyDecision.CONSUME
        }
        val longPress = eventTime - press.downTime >= longPressThresholdMs
        if (!longPress && !released) return PhysicalButtonKeyDecision.CONSUME
        press.gestureEmitted = true
        return if (longPress) PhysicalButtonKeyDecision.LONG_PRESS else PhysicalButtonKeyDecision.SHORT_PRESS
    }

    private companion object {
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
    }
}
