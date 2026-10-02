package com.trancong.dexworkspacetouch.feature.embeddedworkspace.product

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EmbeddedCapabilitySnapshot(
    val platformSupported: Boolean,
    val shizukuBinderAvailable: Boolean,
    val shizukuPermissionGranted: Boolean,
    val shizukuLaunchAvailable: Boolean = false,
    val shizukuPermissionDenied: Boolean = false,
)

fun interface EmbeddedCapabilityProbe {
    fun snapshot(): EmbeddedCapabilitySnapshot
}

sealed interface EmbeddedReadinessResult {
    data object Ready : EmbeddedReadinessResult
    data object UnsupportedPlatform : EmbeddedReadinessResult
    data object ShizukuUnavailable : EmbeddedReadinessResult
    data object ShizukuPermissionMissing : EmbeddedReadinessResult
    data object GeometryUnavailable : EmbeddedReadinessResult
    data object RendererNotReady : EmbeddedReadinessResult
}

object EmbeddedWorkspaceReadiness {
    fun evaluate(
        capability: EmbeddedCapabilitySnapshot,
        geometryReady: Boolean,
        rendererReady: Boolean,
        controllerCanStart: Boolean,
    ): EmbeddedReadinessResult = when {
        !capability.platformSupported -> EmbeddedReadinessResult.UnsupportedPlatform
        !capability.shizukuBinderAvailable -> EmbeddedReadinessResult.ShizukuUnavailable
        !capability.shizukuPermissionGranted -> EmbeddedReadinessResult.ShizukuPermissionMissing
        !geometryReady -> EmbeddedReadinessResult.GeometryUnavailable
        !rendererReady || !controllerCanStart -> EmbeddedReadinessResult.RendererNotReady
        else -> EmbeddedReadinessResult.Ready
    }

    fun withRecovery(reason: EmbeddedReadinessResult, current: EmbeddedProductRecovery): EmbeddedProductRecovery {
        // Refresh chỉ cập nhật lỗi môi trường trước Start, không tạo ownership/clean evidence.
        // Classic phải đã được policy hiện tại cho phép; readiness không tự mở Classic.
        if (current.phase != ProductRunPhase.IDLE || EmbeddedRecoveryAction.OPEN_CLASSIC !in current.permittedActions) return current
        when (current.issue) {
            null, EmbeddedProductIssue.UnsupportedPlatform, EmbeddedProductIssue.ShizukuUnavailable,
            EmbeddedProductIssue.ShizukuPermissionMissing, is EmbeddedProductIssue.GeometryUnavailable,
            is EmbeddedProductIssue.RendererNotReady -> Unit
            else -> return current
        }
        val issue = EmbeddedProductRecoveryMapper.readiness(reason, current.phase, true).issue
        return EmbeddedProductRecoveryMapper.snapshot(current.phase, true, issue,
            current.allocationEvidence, current.cleanupEvidence)
    }
}

data class EmbeddedReadinessSnapshot(val capability: EmbeddedCapabilitySnapshot, val readiness: EmbeddedReadinessResult) {
    val permissionDenied: Boolean get() = capability.shizukuPermissionDenied && readiness == EmbeddedReadinessResult.ShizukuPermissionMissing
}

const val EMBEDDED_SHIZUKU_PERMISSION_REQUEST_CODE = 41008

interface EmbeddedShizukuCallbacks {
    fun listenBinderReceived(listener: () -> Unit): AutoCloseable
    fun listenBinderDead(listener: () -> Unit): AutoCloseable
    fun listenPermissionResult(listener: (requestCode: Int, granted: Boolean) -> Unit): AutoCloseable
    fun requestPermission(requestCode: Int)
}

/** Route-scoped: chỉ đọc readiness, không giữ/cấp phát lease, session hoặc run operation. */
class EmbeddedShizukuRefresh(
    private val probe: EmbeddedCapabilityProbe,
    private val callbacks: EmbeddedShizukuCallbacks,
    private val hostReadiness: () -> ProductHostReadiness,
    private val recovery: () -> EmbeddedProductRecovery,
) {
    private val mutableState = MutableStateFlow<EmbeddedReadinessSnapshot?>(null)
    val state = mutableState.asStateFlow()
    private var callbackToken: Any? = null
    private val subscriptions = mutableListOf<AutoCloseable>()
    private var permissionDenied = false

    fun attach() {
        if (callbackToken != null) return
        val token = Any()
        callbackToken = token
        permissionDenied = false
        try {
            subscriptions += callbacks.listenBinderReceived {
                if (callbackToken === token) { permissionDenied = false; refresh() }
            }
            subscriptions += callbacks.listenBinderDead {
                if (callbackToken === token) { permissionDenied = false; refresh() }
            }
            subscriptions += callbacks.listenPermissionResult { requestCode, granted ->
                if (callbackToken === token && requestCode == EMBEDDED_SHIZUKU_PERMISSION_REQUEST_CODE) {
                    permissionDenied = !granted
                    refresh()
                }
            }
            refresh()
        } catch (failure: Exception) {
            dispose()
            throw failure
        }
    }

    fun refresh() {
        if (callbackToken == null) return
        val observed = probe.snapshot()
        val capability = observed.copy(shizukuPermissionDenied = observed.shizukuBinderAvailable &&
            !observed.shizukuPermissionGranted && (observed.shizukuPermissionDenied || permissionDenied))
        val host = hostReadiness()
        mutableState.value = EmbeddedReadinessSnapshot(capability, EmbeddedWorkspaceReadiness.evaluate(
            capability, true, host.rendererReady, host.controllerCanStart,
        ))
    }

    fun onResume() = refresh()

    /** Chỉ được gọi từ tap Cấp quyền; kiểm tra lại readiness và action policy tại thời điểm tap. */
    fun requestPermission(): Boolean {
        if (callbackToken == null) return false
        refresh()
        if (mutableState.value?.readiness != EmbeddedReadinessResult.ShizukuPermissionMissing ||
            EmbeddedRecoveryAction.REFRESH_READINESS !in recovery().permittedActions) return false
        return runCatching { callbacks.requestPermission(EMBEDDED_SHIZUKU_PERMISSION_REQUEST_CODE) }.isSuccess
    }

    fun dispose() {
        callbackToken = null
        val removing = subscriptions.toList()
        subscriptions.clear()
        removing.forEach { it.close() }
    }
}
