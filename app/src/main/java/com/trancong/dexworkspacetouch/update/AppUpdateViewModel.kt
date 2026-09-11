package com.trancong.dexworkspacetouch.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class Available(val update: AppUpdate) : AppUpdateUiState
    data object Current : AppUpdateUiState
    data object Unavailable : AppUpdateUiState
}

class AppUpdateViewModel(private val repository: AppUpdateRepository) : ViewModel() {
    var state: AppUpdateUiState by mutableStateOf(AppUpdateUiState.Idle)
        private set

    fun check() {
        if (state == AppUpdateUiState.Checking) return
        state = AppUpdateUiState.Checking
        viewModelScope.launch {
            state = when (val result = repository.check()) {
                is AppUpdateResult.Available -> AppUpdateUiState.Available(result.update)
                AppUpdateResult.Current -> AppUpdateUiState.Current
                AppUpdateResult.Unavailable -> AppUpdateUiState.Unavailable
            }
        }
    }

    companion object {
        fun factory(repository: AppUpdateRepository): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppUpdateViewModel(repository) as T
        }
    }
}
