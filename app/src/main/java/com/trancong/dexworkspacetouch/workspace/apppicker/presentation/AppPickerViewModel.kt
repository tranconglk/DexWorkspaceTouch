package com.trancong.dexworkspacetouch.workspace.apppicker.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledApp
import com.trancong.dexworkspacetouch.workspace.apppicker.model.InstalledAppCatalog
import com.trancong.dexworkspacetouch.workspace.apppicker.model.AppIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppPickerViewModel(
    private val catalog: InstalledAppCatalog,
    private val iconLoader: AppIconLoader = FallbackAppIconLoader,
    val selectedIdentity: AppIdentity? = null,
) : ViewModel() {
    var query by mutableStateOf("")
        private set

    var filter by mutableStateOf(AppFilter.ALL)
        private set

    var state by mutableStateOf(AppPickerUiState(isLoading = true))
        private set

    fun updateQuery(query: String) {
        this.query = query
    }

    fun updateFilter(filter: AppFilter) {
        this.filter = filter
    }

    suspend fun loadApps() {
        state = AppPickerUiState(isLoading = true)
        state = withContext(Dispatchers.IO) {
            try {
                AppPickerUiState(apps = catalog.getApps())
            } catch (_: Exception) {
                AppPickerUiState(loadFailed = true)
            }
        }
    }

    suspend fun retry() = loadApps()

    suspend fun loadIcon(identity: AppIdentity): AppIconState =
        withContext(Dispatchers.IO) { iconLoader.loadIcon(identity) }

    val filteredApps: List<InstalledApp>
        get() {
            val term = query.trim()
            return state.apps.filter { app ->
                val matchesFilter = when (filter) {
                    AppFilter.ALL -> true
                    AppFilter.USER -> !app.isSystemApp
                    AppFilter.SYSTEM -> app.isSystemApp
                }
                val matchesQuery = term.isEmpty() ||
                    app.label.contains(term, ignoreCase = true) ||
                    app.packageName.contains(term, ignoreCase = true)
                matchesFilter && matchesQuery
            }
        }

    fun isSelected(app: InstalledApp): Boolean = app.identity == selectedIdentity

    companion object {
        fun factory(
            catalog: InstalledAppCatalog,
            iconLoader: AppIconLoader,
            selectedIdentity: AppIdentity?,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AppPickerViewModel::class.java))
                    return AppPickerViewModel(catalog, iconLoader, selectedIdentity) as T
                }
            }
    }
}

data class AppPickerUiState(
    val apps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
)

private object FallbackAppIconLoader : AppIconLoader {
    override fun loadIcon(identity: AppIdentity): AppIconState = AppIconState.Fallback
}
