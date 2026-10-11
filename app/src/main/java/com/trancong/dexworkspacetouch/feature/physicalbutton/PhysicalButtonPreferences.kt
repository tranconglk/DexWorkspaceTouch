package com.trancong.dexworkspacetouch.feature.physicalbutton

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhysicalButtonConfiguration(
    val enabled: Boolean = false,
    val volumeUpWorkspaceId: String? = null,
    val volumeDownWorkspaceId: String? = null,
    val deviceDescriptor: String? = null,
    val deviceName: String? = null,
) {
    fun workspaceForKey(keyCode: Int): String? = when (keyCode) {
        24 -> volumeUpWorkspaceId
        25 -> volumeDownWorkspaceId
        else -> null
    }?.takeIf(String::isNotBlank)
}

interface PhysicalButtonStorage {
    fun read(): PhysicalButtonConfiguration
    fun write(configuration: PhysicalButtonConfiguration)
}

class PhysicalButtonPreferences(private val storage: PhysicalButtonStorage) {
    private val mutableConfiguration = MutableStateFlow(storage.read())
    val configuration: StateFlow<PhysicalButtonConfiguration> = mutableConfiguration.asStateFlow()
    private val mutableBindingRequested = MutableStateFlow(false)
    val bindingRequested: StateFlow<Boolean> = mutableBindingRequested.asStateFlow()

    @Synchronized
    fun beginBinding() {
        mutableBindingRequested.value = true
    }

    @Synchronized
    fun cancelBinding() {
        mutableBindingRequested.value = false
    }

    @Synchronized
    fun clearRemoteBinding() {
        mutableBindingRequested.value = false
        update(mutableConfiguration.value.copy(deviceDescriptor = null, deviceName = null))
    }

    @Synchronized
    fun captureRemote(deviceDescriptor: String?, deviceName: String?): Boolean {
        if (!mutableBindingRequested.value || deviceDescriptor.isNullOrBlank()) return false
        update(mutableConfiguration.value.copy(
            deviceDescriptor = deviceDescriptor,
            deviceName = deviceName?.takeIf(String::isNotBlank),
        ))
        mutableBindingRequested.value = false
        return true
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        update(mutableConfiguration.value.copy(enabled = enabled))
    }

    @Synchronized
    fun setVolumeUpWorkspace(workspaceId: String?) {
        update(mutableConfiguration.value.copy(volumeUpWorkspaceId = workspaceId?.takeIf(String::isNotBlank)))
    }

    @Synchronized
    fun setVolumeDownWorkspace(workspaceId: String?) {
        update(mutableConfiguration.value.copy(volumeDownWorkspaceId = workspaceId?.takeIf(String::isNotBlank)))
    }

    private fun update(configuration: PhysicalButtonConfiguration) {
        storage.write(configuration)
        mutableConfiguration.value = configuration
    }
}
