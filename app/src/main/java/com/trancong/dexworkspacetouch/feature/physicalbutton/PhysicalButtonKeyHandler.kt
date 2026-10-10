package com.trancong.dexworkspacetouch.feature.physicalbutton

data class PhysicalButtonKeyEvent(
    val keyCode: Int,
    val action: Int,
    val repeatCount: Int,
    val deviceId: Int,
    val downTime: Long,
    val isExternal: Boolean,
    val deviceDescriptor: String? = null,
)

enum class PhysicalButtonKeyDecision { PASS_THROUGH, CONSUME, TRIGGER_WORKSPACE }

class PhysicalButtonKeyHandler {
    private val consumedPresses = mutableMapOf<Int, Long>()

    fun handle(
        event: PhysicalButtonKeyEvent,
        enabled: Boolean,
        configuredDeviceDescriptor: String? = null,
    ): PhysicalButtonKeyDecision {
        if (event.keyCode != VOLUME_UP) return PhysicalButtonKeyDecision.PASS_THROUGH

        // Kết thúc cả chuỗi đã nhận, kể cả khi cấu hình đổi trong lúc giữ nút.
        if (consumedPresses[event.deviceId] == event.downTime) {
            when (event.action) {
                ACTION_DOWN -> return PhysicalButtonKeyDecision.CONSUME
                ACTION_UP -> {
                    consumedPresses.remove(event.deviceId)
                    return PhysicalButtonKeyDecision.CONSUME
                }
            }
        }
        if (!enabled || !event.isExternal || event.action != ACTION_DOWN || event.repeatCount != 0) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        if (configuredDeviceDescriptor != null && event.deviceDescriptor != configuredDeviceDescriptor) {
            return PhysicalButtonKeyDecision.PASS_THROUGH
        }
        consumedPresses[event.deviceId] = event.downTime
        return PhysicalButtonKeyDecision.TRIGGER_WORKSPACE
    }

    private companion object {
        const val VOLUME_UP = 24
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
    }
}
