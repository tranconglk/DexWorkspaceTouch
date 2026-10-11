package com.trancong.dexworkspacetouch.feature.physicalbutton

import android.content.ComponentName
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

internal data class PhysicalButtonAccessibilityState(val grantEnabled: Boolean, val runtimeReady: Boolean)

/** Every entry must parse; a runtime service list can never replace the secure grant. */
internal fun resolvePhysicalButtonAccessibilityGrant(
    enabledServices: String?, globalFlag: Int, matchesComponent: (String) -> Boolean?,
): Boolean = runCatching {
    if (globalFlag != 1 || enabledServices.isNullOrBlank()) return@runCatching false
    var granted = false
    for (entry in enabledServices.split(':')) {
        val matches = matchesComponent(entry.trim()) ?: return@runCatching false
        granted = granted || matches
    }
    granted
}.getOrDefault(false)

internal fun resolvePhysicalButtonAccessibilityGrant(
    enabledServices: String?, globalFlag: Int, serviceComponent: ComponentName,
): Boolean = resolvePhysicalButtonAccessibilityGrant(enabledServices, globalFlag) { entry ->
    ComponentName.unflattenFromString(entry)?.takeIf {
        it.packageName.isNotBlank() && it.className.isNotBlank()
    }?.let { it == serviceComponent }
}

internal class PhysicalButtonAccessibilityStateReader(
    private val readGrantEnabled: () -> Boolean,
    private val readRuntimeReady: () -> Boolean,
) {
    fun read(): PhysicalButtonAccessibilityState = PhysicalButtonAccessibilityState(
        grantEnabled = runCatching(readGrantEnabled).getOrDefault(false),
        runtimeReady = runCatching(readRuntimeReady).getOrDefault(false),
    )
}

internal class PhysicalButtonAccessibilityStateObserver(
    private val readState: () -> PhysicalButtonAccessibilityState,
    private val publish: (PhysicalButtonAccessibilityState) -> Unit,
) : LifecycleEventObserver {
    fun refresh() { publish(readState()) }
    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_RESUME) refresh()
    }
}
