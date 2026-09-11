package com.trancong.dexworkspacetouch.license.runtime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trancong.dexworkspacetouch.license.activation.LicenseActivationResult
import com.trancong.dexworkspacetouch.license.activation.LicenseRepository
import com.trancong.dexworkspacetouch.license.domain.LicenseErrorCode
import com.trancong.dexworkspacetouch.license.domain.LicenseFailure
import com.trancong.dexworkspacetouch.license.domain.LicenseKey
import com.trancong.dexworkspacetouch.license.domain.LicenseState
import com.trancong.dexworkspacetouch.license.domain.LicenseTimeProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.min

class LicenseRuntimeCoordinator(
    private val repository: LicenseRepository,
    private val timeProvider: LicenseTimeProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LicenseGateUiState>(LicenseGateUiState.Checking)
    val state: StateFlow<LicenseGateUiState> = mutableState.asStateFlow()
    private var refreshJob: Job? = null

    init { bootstrap() }

    fun bootstrap() {
        mutableState.value = LicenseGateUiState.Checking
        viewModelScope.launch {
            val stored = try {
                if (repository.loadStoredToken() == null) activationRequired(LicenseState.Unactivated)
                else repository.verifyStoredToken().toStoredGateState(nowEpochSeconds())
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                activationRequired(LicenseState.Error(LicenseFailure.LocalDataCorrupted))
            }
            mutableState.value = stored
            if (stored is LicenseGateUiState.Allowed) refreshIfDue()
        }
    }

    fun refreshIfDue() {
        if (refreshJob?.isActive == true) return
        val allowed = mutableState.value as? LicenseGateUiState.Allowed ?: return
        val claims = when (val licenseState = allowed.licenseState) {
            is LicenseState.Active -> licenseState.claims
            is LicenseState.OfflineGrace -> licenseState.claims
            else -> return
        }
        refreshJob = viewModelScope.launch {
            when (val result = repository.refreshIfDue(claims)) {
                is com.trancong.dexworkspacetouch.license.activation.LicenseRefreshResult.Success ->
                    mutableState.value = LicenseGateUiState.Allowed(result.state)
                is com.trancong.dexworkspacetouch.license.activation.LicenseRefreshResult.AuthoritativeFailure ->
                    mutableState.value = activationRequired(result.failure.toLicenseState())
                com.trancong.dexworkspacetouch.license.activation.LicenseRefreshResult.NotDue,
                com.trancong.dexworkspacetouch.license.activation.LicenseRefreshResult.TransientFailure,
                com.trancong.dexworkspacetouch.license.activation.LicenseRefreshResult.IntegrityFailure -> Unit
            }
        }
    }

    fun updateLicenseKey(value: String) {
        val current = mutableState.value as? LicenseGateUiState.ActivationRequired ?: return
        if (current.activating) return
        mutableState.value = current.copy(licenseKeyInput = value, validationMessage = null, activationFailure = null)
    }

    fun activate() {
        val current = mutableState.value as? LicenseGateUiState.ActivationRequired ?: return
        if (current.activating) return
        val key = try {
            LicenseKey.parse(current.licenseKeyInput)
        } catch (_: IllegalArgumentException) {
            mutableState.value = current.copy(validationMessage = "License Key không hợp lệ.")
            return
        }
        mutableState.value = current.copy(activating = true, validationMessage = null, activationFailure = null)
        viewModelScope.launch {
            val result = try {
                repository.activate(key)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                LicenseActivationResult.Failure(LicenseFailure.Unexpected, null)
            }
            mutableState.value = when (result) {
                is LicenseActivationResult.Success -> LicenseGateUiState.Allowed(result.state)
                is LicenseActivationResult.Failure -> handleActivationFailure(result, current)
            }
        }
    }

    private suspend fun handleActivationFailure(
        result: LicenseActivationResult.Failure,
        previous: LicenseGateUiState.ActivationRequired,
    ): LicenseGateUiState {
        if (result.failure is LicenseFailure.NetworkUnavailable && repository.loadStoredToken() != null) {
            val stored = repository.verifyStoredToken().toStoredGateState(nowEpochSeconds())
            if (stored is LicenseGateUiState.Allowed) return stored
        }
        val domainState = result.failure.toLicenseState()
        return activationRequired(domainState).copy(
            licenseKeyInput = previous.licenseKeyInput,
            activationFailure = result.failure,
            requestId = result.requestId,
        )
    }

    private fun LicenseState.toStoredGateState(nowEpochSeconds: Long): LicenseGateUiState = when (this) {
        is LicenseState.Active -> {
            val effectiveLocalEnd = min(claims.expiresAtEpochSeconds, claims.offlineValidUntilEpochSeconds)
            if (nowEpochSeconds >= effectiveLocalEnd) activationRequired(LicenseState.Expired(claims.licenseId))
            else LicenseGateUiState.Allowed(LicenseState.OfflineGrace(claims))
        }
        is LicenseState.Error -> activationRequired(failure.toLicenseState())
        else -> if (allowsMainApplication()) LicenseGateUiState.Allowed(this) else activationRequired(this)
    }

    private fun LicenseFailure.toLicenseState(): LicenseState = when (this) {
        is LicenseFailure.Rejected -> when (code) {
            LicenseErrorCode.LICENSE_INVALID -> LicenseState.Unactivated
            LicenseErrorCode.LICENSE_REVOKED -> LicenseState.Revoked(null)
            LicenseErrorCode.LICENSE_EXPIRED, LicenseErrorCode.TOKEN_EXPIRED -> LicenseState.Expired(null)
            LicenseErrorCode.DEVICE_MISMATCH -> LicenseState.DeviceMismatch(null)
            LicenseErrorCode.DEVICE_REVOKED -> LicenseState.DeviceRevoked(null)
            else -> LicenseState.Error(this)
        }
        LicenseFailure.NetworkUnavailable -> LicenseState.NetworkRequired(null)
        else -> LicenseState.Error(this)
    }

    private fun activationRequired(state: LicenseState) = LicenseGateUiState.ActivationRequired(licenseState = state)
    private fun nowEpochSeconds(): Long = timeProvider.nowEpochMillis() / 1_000L

    companion object {
        fun factory(repository: LicenseRepository, timeProvider: LicenseTimeProvider): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LicenseRuntimeCoordinator(repository, timeProvider) as T
            }
    }
}
