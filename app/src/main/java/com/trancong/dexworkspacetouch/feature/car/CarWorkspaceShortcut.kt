package com.trancong.dexworkspacetouch.feature.car

import kotlinx.coroutines.flow.StateFlow
import java.util.Collections

enum class CarWorkspaceShortcutSlot(
    val stableKey: String,
    val stableWorkflowId: String,
    val displayLabel: String,
) {
    Slot1("slot-1", "workspace-shortcut-slot-1-v1", "Slot 1"),
    Slot2("slot-2", "workspace-shortcut-slot-2-v1", "Slot 2"),
    Slot3("slot-3", "workspace-shortcut-slot-3-v1", "Slot 3"),
    Slot4("slot-4", "workspace-shortcut-slot-4-v1", "Slot 4"),
    Slot5("slot-5", "workspace-shortcut-slot-5-v1", "Slot 5"),
    Slot6("slot-6", "workspace-shortcut-slot-6-v1", "Slot 6"),
    Slot7("slot-7", "workspace-shortcut-slot-7-v1", "Slot 7"),
    Slot8("slot-8", "workspace-shortcut-slot-8-v1", "Slot 8"),
}

data class CarWorkspaceShortcut(
    val slot: CarWorkspaceShortcutSlot,
    val workspaceId: String?,
) {
    init {
        require(workspaceId == null || workspaceId.isNotBlank()) {
            "workspaceId must be null or non-blank"
        }
    }
}

class CarWorkspaceShortcuts private constructor(
    shortcuts: Map<CarWorkspaceShortcutSlot, String?>,
) {
    private val bySlot = shortcuts.toMap()

    operator fun get(slot: CarWorkspaceShortcutSlot): CarWorkspaceShortcut =
        CarWorkspaceShortcut(slot, bySlot.getValue(slot))

    val configurations: List<CarWorkspaceShortcut>
        get() = Collections.unmodifiableList(
            CarWorkspaceShortcutSlot.entries.map(::get),
        )

    override fun equals(other: Any?): Boolean =
        other is CarWorkspaceShortcuts && bySlot == other.bySlot

    override fun hashCode(): Int = bySlot.hashCode()

    companion object {
        fun defaults(): CarWorkspaceShortcuts = from { null }

        internal fun from(
            workspaceIdFor: (CarWorkspaceShortcutSlot) -> String?,
        ): CarWorkspaceShortcuts = CarWorkspaceShortcuts(
            CarWorkspaceShortcutSlot.entries.associateWith(workspaceIdFor),
        )
    }
}

interface CarWorkspaceShortcutPreferences {
    val shortcuts: StateFlow<CarWorkspaceShortcuts>
    val visibleSlotCount: StateFlow<Int>
    fun setWorkspace(slot: CarWorkspaceShortcutSlot, workspaceId: String)
    fun clear(slot: CarWorkspaceShortcutSlot)
    fun setVisibleSlotCount(count: Int)
    fun refresh()
}

object CarWorkspaceShortcutCapacity {
    const val MinimumVisible = 3
    const val Maximum = 8
    const val DefaultVisible = 6

    fun normalizeVisibleCount(count: Int): Int = count.coerceIn(MinimumVisible, Maximum)
}

fun interface CarWorkspaceShortcutWorkflowProvider {
    fun workflowFor(slot: CarWorkspaceShortcutSlot): CarWorkflow?
}

class PreferencesCarWorkspaceShortcutWorkflowProvider(
    private val preferences: CarWorkspaceShortcutPreferences,
) : CarWorkspaceShortcutWorkflowProvider {
    override fun workflowFor(slot: CarWorkspaceShortcutSlot): CarWorkflow? {
        val workspaceId = preferences.shortcuts.value[slot].workspaceId ?: return null
        return CarWorkflow(
            id = slot.stableWorkflowId,
            actions = listOf(CarAction.Workspace(workspaceId)),
        )
    }
}
