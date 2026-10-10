package com.trancong.dexworkspacetouch.feature.physicalbutton

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PhysicalButtonConfiguration(val enabled: Boolean = false, val workspaceId: String? = null)

interface PhysicalButtonStorage {
    fun read(): PhysicalButtonConfiguration
    fun write(configuration: PhysicalButtonConfiguration)
}

class PhysicalButtonPreferences(private val storage: PhysicalButtonStorage) {
    private val mutableConfiguration = MutableStateFlow(storage.read())
    val configuration: StateFlow<PhysicalButtonConfiguration> = mutableConfiguration.asStateFlow()

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        update(mutableConfiguration.value.copy(enabled = enabled))
    }

    @Synchronized
    fun setWorkspace(workspaceId: String?) {
        update(mutableConfiguration.value.copy(workspaceId = workspaceId?.takeIf(String::isNotBlank)))
    }

    private fun update(configuration: PhysicalButtonConfiguration) {
        storage.write(configuration)
        mutableConfiguration.value = configuration
    }
}
