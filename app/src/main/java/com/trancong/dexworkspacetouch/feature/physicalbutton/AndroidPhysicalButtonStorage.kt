package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.content.Context
import android.content.SharedPreferences

class AndroidPhysicalButtonStorage(private val preferences: SharedPreferences) : PhysicalButtonStorage {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("physical_button", Context.MODE_PRIVATE),
    )

    override fun read(): PhysicalButtonConfiguration {
        val stored = preferences.all
        val migrated = stored[MIGRATED_KEY] == true
        val hasNewMapping = stored.containsKey("volumeUpWorkspaceId") || stored.containsKey("volumeDownWorkspaceId")
        val upKey = if (migrated || hasNewMapping) "volumeUpWorkspaceId" else "workspaceId"
        val configuration = PhysicalButtonConfiguration(
            enabled = stored["enabled"] as? Boolean ?: false,
            volumeUpWorkspaceId = (stored[upKey] as? String)?.takeIf(String::isNotBlank),
            volumeDownWorkspaceId = (stored["volumeDownWorkspaceId"] as? String)?.takeIf(String::isNotBlank),
            deviceDescriptor = (stored["deviceDescriptor"] as? String)?.takeIf(String::isNotBlank),
            deviceName = (stored["deviceName"] as? String)?.takeIf(String::isNotBlank),
        )
        if (!migrated) {
            // Ghi mapping và marker cùng một editor; giữ nguyên legacy key và remote identity.
            preferences.edit()
                .putString("volumeUpWorkspaceId", configuration.volumeUpWorkspaceId)
                .putString("volumeDownWorkspaceId", configuration.volumeDownWorkspaceId)
                .putBoolean(MIGRATED_KEY, true)
                .apply()
        }
        return configuration
    }

    override fun write(configuration: PhysicalButtonConfiguration) {
        preferences.edit().apply {
            putBoolean("enabled", configuration.enabled)
            putString("volumeUpWorkspaceId", configuration.volumeUpWorkspaceId)
            putString("volumeDownWorkspaceId", configuration.volumeDownWorkspaceId)
            putBoolean(MIGRATED_KEY, true)
            if (configuration.deviceDescriptor == null) remove("deviceDescriptor")
            else putString("deviceDescriptor", configuration.deviceDescriptor)
            if (configuration.deviceName == null) remove("deviceName")
            else putString("deviceName", configuration.deviceName)
        }.apply()
    }

    private companion object { const val MIGRATED_KEY = "dualWorkspaceMappingMigrated" }
}
