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
)

enum class PhysicalButtonKeyDecision { PASS_THROUGH, CONSUME, BIND_REMOTE, TRIGGER_WORKSPACE }

class PhysicalButtonKeyHandler {
    private data class ConsumedPress(val downTime: Long, val descriptor: String)
    private data class PressKey(val deviceId: Int, val keyCode: Int)
    // deviceId chỉ ghép DOWN/UP của lần nhấn hiện tại, không dùng làm binding identity.
    private val consumedPresses = mutableMapOf<PressKey, ConsumedPress>()

    fun handle(
        event: PhysicalButtonKeyEvent,
        configuration: PhysicalButtonConfiguration,
        availableWorkspaceIds: Set<String>,
        bindingRequested: Boolean = false,
    ): PhysicalButtonKeyDecision = handle(
        event = event,
        enabled = configuration.enabled && configuration.workspaceForKey(event.keyCode) in availableWorkspaceIds,
        configuredDeviceDescriptor = configuration.deviceDescriptor,
        bindingRequested = bindingRequested,
    )

    fun handle(
        event: PhysicalButtonKeyEvent,
        enabled: Boolean,
        configuredDeviceDescriptor: String? = null,
        bindingRequested: Boolean = false,
    ): PhysicalButtonKeyDecision {
        if ((event.keyCode != VOLUME_UP && event.keyCode != VOLUME_DOWN) || !event.isExternal || event.isVirtual) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        val descriptor = event.deviceDescriptor?.takeIf(String::isNotBlank)
            ?: return PhysicalButtonKeyDecision.PASS_THROUGH

        // Kết thúc cả chuỗi đã nhận, kể cả khi cấu hình đổi trong lúc giữ nút.
        val pressKey = PressKey(event.deviceId, event.keyCode)
        if (consumedPresses[pressKey] == ConsumedPress(event.downTime, descriptor)) {
            when (event.action) {
                ACTION_DOWN -> return PhysicalButtonKeyDecision.CONSUME
                ACTION_UP -> {
                    consumedPresses.remove(pressKey)
                    return PhysicalButtonKeyDecision.CONSUME
                }
            }
        }
        if (event.action != ACTION_DOWN || event.repeatCount != 0) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        if (bindingRequested) {
            if (event.keyCode != VOLUME_UP) return PhysicalButtonKeyDecision.PASS_THROUGH
            consumedPresses[pressKey] = ConsumedPress(event.downTime, descriptor)
            return PhysicalButtonKeyDecision.BIND_REMOTE
        }
        if (!enabled || configuredDeviceDescriptor.isNullOrBlank() || descriptor != configuredDeviceDescriptor) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        consumedPresses[pressKey] = ConsumedPress(event.downTime, descriptor)
        return PhysicalButtonKeyDecision.TRIGGER_WORKSPACE
    }

    private companion object {
        const val VOLUME_UP = 24
        const val VOLUME_DOWN = 25
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
    }
}
