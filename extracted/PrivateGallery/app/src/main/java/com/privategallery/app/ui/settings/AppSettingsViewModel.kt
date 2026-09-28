package com.privategallery.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppSettingsUiState(val username: String = "", val biometricLockEnabled: Boolean = false)

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppSettingsUiState())
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.value = _uiState.value.copy(
                    username = user?.username ?: "",
                    biometricLockEnabled = authRepository.isBiometricLockEnabled()
                )
            }
        }
    }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch {
            authRepository.setBiometricLockEnabled(enabled)
            _uiState.value = _uiState.value.copy(biometricLockEnabled = enabled)
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
