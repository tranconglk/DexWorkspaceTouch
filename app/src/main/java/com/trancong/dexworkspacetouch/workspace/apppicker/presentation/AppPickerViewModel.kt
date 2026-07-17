package com.trancong.dexworkspacetouch.workspace.apppicker.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppCatalog

class AppPickerViewModel(
    private val catalog: InstalledAppCatalog,
) : ViewModel() {
    var query by mutableStateOf("")
        private set

    var state by mutableStateOf(AppPickerUiState())
        private set

    init {
        loadApps()
    }

    fun updateQuery(query: String) {
        this.query = query
    }

    fun loadApps() {
        state = try {
            AppPickerUiState(apps = catalog.getApps())
        } catch (_: Exception) {
            AppPickerUiState(loadFailed = true)
        }
    }

    val filteredApps: List<InstalledApp>
        get() {
            val term = query.trim()
            if (term.isEmpty()) return state.apps
            return state.apps.filter { app ->
                app.label.contains(term, ignoreCase = true) ||
                    app.packageName.contains(term, ignoreCase = true)
            }
        }

    companion object {
        fun factory(catalog: InstalledAppCatalog): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AppPickerViewModel::class.java))
                    return AppPickerViewModel(catalog) as T
                }
            }
    }
}

data class AppPickerUiState(
    val apps: List<InstalledApp> = emptyList(),
    val loadFailed: Boolean = false,
)
