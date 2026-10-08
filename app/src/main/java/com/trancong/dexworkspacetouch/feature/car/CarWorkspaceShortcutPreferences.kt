package com.trancong.dexworkspacetouch.feature.car

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface CarWorkspaceShortcutStorage {
    fun read(key: String): String?
    fun write(key: String, value: String?)
}

class StoredCarWorkspaceShortcutPreferences(
    private val storage: CarWorkspaceShortcutStorage,
) : CarWorkspaceShortcutPreferences {
    private val mutableShortcuts = MutableStateFlow(readSnapshot())
    override val shortcuts: StateFlow<CarWorkspaceShortcuts> = mutableShortcuts.asStateFlow()
    private val mutableVisibleSlotCount = MutableStateFlow(readVisibleSlotCount())
    override val visibleSlotCount: StateFlow<Int> = mutableVisibleSlotCount.asStateFlow()
    private val mutableAutoRepairEnabled = MutableStateFlow(readAutoRepairEnabled())
    override val autoRepairEnabled: StateFlow<Boolean> = mutableAutoRepairEnabled.asStateFlow()

    @Synchronized
    override fun setWorkspace(slot: CarWorkspaceShortcutSlot, workspaceId: String) {
        require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
        storage.write(slot.stableKey, workspaceId)
        mutableShortcuts.value = readSnapshot()
    }

    @Synchronized
    override fun clear(slot: CarWorkspaceShortcutSlot) {
        storage.write(slot.stableKey, null)
        mutableShortcuts.value = readSnapshot()
    }

    @Synchronized
    override fun setVisibleSlotCount(count: Int) {
        val normalized = CarWorkspaceShortcutCapacity.normalizeVisibleCount(count)
        storage.write(VisibleSlotCountKey, normalized.toString())
        mutableVisibleSlotCount.value = normalized
    }

    @Synchronized
    override fun setAutoRepairEnabled(enabled: Boolean) {
        storage.write(AutoRepairEnabledKey, enabled.toString())
        mutableAutoRepairEnabled.value = enabled
    }

    @Synchronized
    override fun refresh() {
        mutableShortcuts.value = readSnapshot()
        mutableVisibleSlotCount.value = readVisibleSlotCount()
        mutableAutoRepairEnabled.value = readAutoRepairEnabled()
    }

    private fun readSnapshot(): CarWorkspaceShortcuts = CarWorkspaceShortcuts.from { slot ->
        storage.read(slot.stableKey)?.takeIf(String::isNotBlank)
    }

    private fun readVisibleSlotCount(): Int {
        val stored = storage.read(VisibleSlotCountKey)
            ?: return CarWorkspaceShortcutCapacity.DefaultVisible
        val parsed = stored.toIntOrNull()
            ?: return CarWorkspaceShortcutCapacity.DefaultVisible
        return CarWorkspaceShortcutCapacity.normalizeVisibleCount(parsed)
    }

    private fun readAutoRepairEnabled(): Boolean {
        storage.read(AutoRepairEnabledKey)?.let { return it == "true" }
        val enabled = storage.read(WorkspaceRepairModeKey) == WorkspaceRepairMode.AUTOMATIC.name
        storage.write(AutoRepairEnabledKey, enabled.toString())
        return enabled
    }

    private companion object {
        const val VisibleSlotCountKey = "visibleSlotCount"
        const val WorkspaceRepairModeKey = "workspaceRepairMode"
        const val AutoRepairEnabledKey = "autoRepairEnabled"
    }
}

class SharedPreferencesCarWorkspaceShortcutStorage(
    private val preferences: SharedPreferences,
) : CarWorkspaceShortcutStorage {
    override fun read(key: String): String? = preferences.all[key]?.let { it as? String ?: "" }

    override fun write(key: String, value: String?) {
        preferences.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    companion object {
        private const val PreferencesName = "car_workspace_shortcuts"

        fun create(context: Context): SharedPreferencesCarWorkspaceShortcutStorage =
            SharedPreferencesCarWorkspaceShortcutStorage(
                context.applicationContext.getSharedPreferences(
                    PreferencesName,
                    Context.MODE_PRIVATE,
                ),
            )
    }
}

fun createCarWorkspaceShortcutPreferences(context: Context): CarWorkspaceShortcutPreferences =
    StoredCarWorkspaceShortcutPreferences(
        SharedPreferencesCarWorkspaceShortcutStorage.create(context),
    )
