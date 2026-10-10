package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.content.Context
import android.content.SharedPreferences

class AndroidPhysicalButtonStorage(context: Context) : PhysicalButtonStorage {
    private val preferences: SharedPreferences = context.applicationContext.getSharedPreferences(
        "physical_button", Context.MODE_PRIVATE,
    )

    override fun read(): PhysicalButtonConfiguration = PhysicalButtonConfiguration(
        enabled = preferences.all["enabled"] as? Boolean ?: false,
        workspaceId = (preferences.all["workspaceId"] as? String)?.takeIf(String::isNotBlank),
    )

    override fun write(configuration: PhysicalButtonConfiguration) {
        preferences.edit().apply {
            putBoolean("enabled", configuration.enabled)
            if (configuration.workspaceId == null) remove("workspaceId")
            else putString("workspaceId", configuration.workspaceId)
        }.apply()
    }
}
