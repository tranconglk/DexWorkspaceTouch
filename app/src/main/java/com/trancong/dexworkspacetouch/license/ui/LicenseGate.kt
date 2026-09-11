package com.trancong.dexworkspacetouch.license.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.trancong.dexworkspacetouch.license.domain.LicenseErrorCode
import com.trancong.dexworkspacetouch.license.domain.LicenseFailure
import com.trancong.dexworkspacetouch.license.domain.LicenseState
import com.trancong.dexworkspacetouch.license.runtime.LicenseGateUiState
import com.trancong.dexworkspacetouch.update.AppUpdatePanel
import com.trancong.dexworkspacetouch.update.AppUpdateUiState
import com.trancong.dexworkspacetouch.ui.design.DesignerShapes
import com.trancong.dexworkspacetouch.ui.design.Dimensions
import com.trancong.dexworkspacetouch.ui.design.DwtStatusSurface
import com.trancong.dexworkspacetouch.ui.design.DwtStatusTone
import com.trancong.dexworkspacetouch.ui.design.Spacing
import com.trancong.dexworkspacetouch.ui.design.TouchTargets

@Composable
fun LicenseGate(
    state: LicenseGateUiState,
    onLicenseKeyChanged: (String) -> Unit,
    onActivate: () -> Unit,
    updateState: AppUpdateUiState,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDownload: (String) -> Unit,
    mainContent: @Composable () -> Unit,
) {
    when (state) {
        LicenseGateUiState.Checking -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
                DwtStatusSurface(
                    tone = DwtStatusTone.Info,
                    title = "Đang xác minh giấy phép",
                    supportingText = "DexWorkspaceTouch đang kiểm tra quyền sử dụng trên thiết bị này.",
                    modifier = Modifier.widthIn(max = Dimensions.ActivationContentMaxWidth),
                    trailingContent = { CircularProgressIndicator() },
                )
            }
        }
        is LicenseGateUiState.Allowed -> mainContent()
        is LicenseGateUiState.ActivationRequired -> ActivationScreen(
            state, onLicenseKeyChanged, onActivate, updateState, onCheckUpdate, onOpenUpdateDownload,
        )
    }
}

@Composable
private fun ActivationScreen(
    state: LicenseGateUiState.ActivationRequired,
    onLicenseKeyChanged: (String) -> Unit,
    onActivate: () -> Unit,
    updateState: AppUpdateUiState,
    onCheckUpdate: () -> Unit,
    onOpenUpdateDownload: (String) -> Unit,
) {
    ProtectActivationCredentialSurface()
    val presentation = licenseActivationPresentation(state)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(Spacing.L), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = Dimensions.ActivationContentMaxWidth).fillMaxWidth(),
                shape = DesignerShapes.Workspace,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(Spacing.L).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.M),
                ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Text("DexWorkspaceTouch", style = MaterialTheme.typography.titleLarge)
                    Text("Kích hoạt bản quyền", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Nhập License Key đã được cấp để xác minh thiết bị và tiếp tục sử dụng.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DwtStatusSurface(
                    tone = presentation.tone,
                    title = presentation.title,
                    supportingText = presentation.message,
                )
                OutlinedTextField(
                    value = state.licenseKeyInput,
                    onValueChange = onLicenseKeyChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.activating,
                    singleLine = true,
                    label = { Text("License Key") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                    ),
                    visualTransformation = licenseKeyVisualTransformation,
                    isError = state.validationMessage != null || state.activationFailure != null,
                    supportingText = {
                        val message = state.validationMessage ?: state.activationFailure?.userMessage()
                        if (message != null) Text(message)
                    },
                )
                Button(
                    onClick = onActivate,
                    modifier = Modifier.align(Alignment.End)
                        .widthIn(min = 200.dp).height(TouchTargets.SecondaryButton),
                    enabled = !state.activating && state.licenseKeyInput.isNotBlank(),
                ) {
                    if (state.activating) {
                        CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Đang kiểm tra…")
                    } else Text("Kích hoạt")
                }
                state.requestId?.takeIf(String::isNotBlank)?.let {
                    Text(
                        "Mã hỗ trợ: ${it.take(32)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Cập nhật ứng dụng", style = MaterialTheme.typography.titleMedium)
                AppUpdatePanel(
                    state = updateState,
                    onCheck = onCheckUpdate,
                    onOpenDownload = onOpenUpdateDownload,
                    checkEnabled = !state.activating,
                )
                }
            }
        }
    }
}

internal val licenseKeyVisualTransformation: VisualTransformation = PasswordVisualTransformation()

/** Protects only the activation surface; the normal licensed application keeps its existing behavior. */
@Composable
private fun ProtectActivationCredentialSurface() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val wasSecure = window?.attributes?.flags?.and(WindowManager.LayoutParams.FLAG_SECURE) != 0
        val filteredObscuredTouches = view.filterTouchesWhenObscured
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        view.filterTouchesWhenObscured = true
        onDispose {
            if (!wasSecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            view.filterTouchesWhenObscured = filteredObscuredTouches
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal data class LicenseGateContentPolicy(
    val rendersMainApplication: Boolean,
    val exposesActivation: Boolean,
    val exposesPublicUpdate: Boolean,
)

internal fun licenseGateContentPolicy(state: LicenseGateUiState): LicenseGateContentPolicy = when (state) {
    LicenseGateUiState.Checking -> LicenseGateContentPolicy(false, false, false)
    is LicenseGateUiState.Allowed -> LicenseGateContentPolicy(true, false, false)
    is LicenseGateUiState.ActivationRequired -> LicenseGateContentPolicy(false, true, true)
}

internal data class LicenseActivationPresentation(
    val tone: DwtStatusTone,
    val title: String,
    val message: String,
)

internal fun licenseActivationPresentation(
    state: LicenseGateUiState.ActivationRequired,
): LicenseActivationPresentation {
    val failureMessage = state.validationMessage ?: state.activationFailure?.userMessage()
    if (failureMessage != null) {
        return LicenseActivationPresentation(DwtStatusTone.Error, "Không thể kích hoạt", failureMessage)
    }
    val tone = when (state.licenseState) {
        LicenseState.Unactivated, LicenseState.Activating -> DwtStatusTone.Info
        is LicenseState.NetworkRequired, is LicenseState.Expired -> DwtStatusTone.Warning
        is LicenseState.DeviceMismatch, is LicenseState.DeviceRevoked, is LicenseState.Revoked,
        is LicenseState.Error -> DwtStatusTone.Error
        is LicenseState.Active, is LicenseState.OfflineGrace -> DwtStatusTone.Success
    }
    val title = when (tone) {
        DwtStatusTone.Info -> if (state.activating) "Đang kích hoạt" else "Cần kích hoạt"
        DwtStatusTone.Success -> "Giấy phép hợp lệ"
        DwtStatusTone.Warning -> "Cần xác minh lại"
        DwtStatusTone.Error -> "Giấy phép không hợp lệ"
    }
    return LicenseActivationPresentation(tone, title, state.licenseState.userMessage())
}

private fun LicenseState.userMessage(): String = when (this) {
    LicenseState.Unactivated -> "Ứng dụng chưa được kích hoạt. Nhập License Key để tiếp tục."
    LicenseState.Activating -> "Đang kiểm tra License Key…"
    is LicenseState.Expired -> "Phiên xác minh giấy phép đã hết hạn. Kết nối Internet và kích hoạt lại để tiếp tục."
    is LicenseState.DeviceMismatch -> "Giấy phép đã lưu không khớp với thiết bị này. Vui lòng kích hoạt lại."
    is LicenseState.DeviceRevoked -> "Thiết bị này đã bị gỡ khỏi giấy phép. Vui lòng kích hoạt lại."
    is LicenseState.Revoked -> "Giấy phép này đã bị thu hồi."
    is LicenseState.NetworkRequired -> "Cần kết nối Internet để xác minh giấy phép."
    is LicenseState.Error -> "Không thể xác minh giấy phép. Vui lòng thử lại."
    is LicenseState.Active, is LicenseState.OfflineGrace -> "Giấy phép hợp lệ."
}

private fun LicenseFailure.userMessage(): String = when (this) {
    LicenseFailure.NetworkUnavailable -> "Không thể kết nối máy chủ. Kiểm tra Internet và thử lại."
    is LicenseFailure.Server -> "Máy chủ tạm thời không khả dụng. Vui lòng thử lại."
    LicenseFailure.InvalidResponse -> "Phản hồi xác minh không hợp lệ."
    LicenseFailure.LocalDataCorrupted -> "Dữ liệu giấy phép trên thiết bị không hợp lệ."
    LicenseFailure.Unexpected -> "Không thể kích hoạt. Vui lòng thử lại."
    is LicenseFailure.Rejected -> when (code) {
        LicenseErrorCode.LICENSE_INVALID -> "License Key không hợp lệ."
        LicenseErrorCode.LICENSE_REVOKED -> "Giấy phép này đã bị thu hồi."
        LicenseErrorCode.LICENSE_EXPIRED -> "Giấy phép này đã hết hạn."
        LicenseErrorCode.DEVICE_MISMATCH -> "Giấy phép không khớp với thiết bị này."
        LicenseErrorCode.DEVICE_REVOKED -> "Thiết bị này đã bị gỡ khỏi giấy phép."
        LicenseErrorCode.UNKNOWN_KEY -> "Không thể xác minh khóa ký giấy phép."
        LicenseErrorCode.DEVICE_LIMIT_REACHED -> "Giấy phép đã đạt giới hạn thiết bị."
        LicenseErrorCode.APPLICATION_NOT_ALLOWED -> "Phiên bản ứng dụng này không được phép kích hoạt."
        LicenseErrorCode.INVALID_REQUEST, LicenseErrorCode.INVALID_DEVICE_PUBLIC_KEY -> "Không thể xác minh thiết bị."
        LicenseErrorCode.TOKEN_INVALID, LicenseErrorCode.TOKEN_EXPIRED -> "Token giấy phép không hợp lệ hoặc đã hết hạn."
        LicenseErrorCode.RATE_LIMITED -> "Có quá nhiều yêu cầu. Vui lòng thử lại sau."
        else -> "Không thể kích hoạt giấy phép."
    }
}
