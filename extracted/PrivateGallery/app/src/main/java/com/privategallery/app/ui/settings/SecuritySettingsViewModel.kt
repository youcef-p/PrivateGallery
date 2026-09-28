package com.privategallery.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.security.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SecuritySettingsUiState(
    val screenshotProtectionEnabled: Boolean = true,
    val biometricLockEnabled: Boolean = false
)

@HiltViewModel
class SecuritySettingsViewModel @Inject constructor(
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SecuritySettingsUiState(
            screenshotProtectionEnabled = securePreferences.isScreenshotProtectionEnabled(),
            biometricLockEnabled = securePreferences.isBiometricLockEnabled()
        )
    )
    val uiState: StateFlow<SecuritySettingsUiState> = _uiState.asStateFlow()

    fun setScreenshotProtection(enabled: Boolean) {
        securePreferences.setScreenshotProtectionEnabled(enabled)
        _uiState.value = _uiState.value.copy(screenshotProtectionEnabled = enabled)
        // MainActivity re-applies FLAG_SECURE on next onResume; see MainActivity.applySecureModeFlag.
    }

    fun setBiometricLock(enabled: Boolean) {
        securePreferences.setBiometricLockEnabled(enabled)
        _uiState.value = _uiState.value.copy(biometricLockEnabled = enabled)
    }

    /** "Clear local cache" per PRIVACY_REQUIREMENTS — wipes decrypted viewer cache; encrypted
     *  originals under filesDir/encrypted_media are untouched (that's the synced library, not a
     *  cache). */
    fun clearDecryptedCache(cacheDir: java.io.File) {
        viewModelScope.launch {
            cacheDir.resolve("viewer_plaintext").listFiles()?.forEach { it.delete() }
        }
    }
}
