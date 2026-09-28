package com.privategallery.app.ui.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.data.repository.MediaRepository
import com.privategallery.app.security.SecurePreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val securePreferences: SecurePreferences
) : ViewModel() {

    private val folderId: String = checkNotNull(savedStateHandle["folderId"])
    private val initialMediaId: String = checkNotNull(savedStateHandle["mediaId"])

    private val _uiState = MutableStateFlow(MediaViewerUiState(secureModeEnabled = securePreferences.isScreenshotProtectionEnabled()))
    val uiState: StateFlow<MediaViewerUiState> = _uiState.asStateFlow()

    private var lastDecryptedFile: File? = null

    init {
        viewModelScope.launch {
            mediaRepository.observePagedMedia(folderId, 200, 0).collect { items ->
                val index = items.indexOfFirst { it.id == initialMediaId }.coerceAtLeast(0)
                _uiState.value = _uiState.value.copy(isLoading = false, items = items, currentIndex = index)
                if (items.isNotEmpty()) decryptCurrent(items[index].id)
            }
        }
    }

    fun onPageChanged(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index, decryptedPath = null)
        _uiState.value.items.getOrNull(index)?.let { decryptCurrent(it.id) }
    }

    private fun decryptCurrent(mediaId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDecrypting = true, error = null)
            try {
                cleanupLastDecryptedFile()
                val file = mediaRepository.decryptForViewing(folderId, mediaId)
                lastDecryptedFile = file
                _uiState.value = _uiState.value.copy(decryptedPath = file.absolutePath, isDecrypting = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isDecrypting = false, error = "Couldn't decrypt this item: ${e.message}")
            }
        }
    }

    fun deleteCurrent() {
        val current = _uiState.value.items.getOrNull(_uiState.value.currentIndex) ?: return
        viewModelScope.launch {
            mediaRepository.deleteMedia(folderId, current.id)
        }
    }

    /** Plaintext is only ever written to cacheDir for the lifetime of viewing — cleared here and
     *  again in onCleared() so nothing decrypted lingers on disk once the viewer isn't in use. */
    private fun cleanupLastDecryptedFile() {
        lastDecryptedFile?.let { if (it.exists()) it.delete() }
        lastDecryptedFile = null
    }

    override fun onCleared() {
        cleanupLastDecryptedFile()
        super.onCleared()
    }
}
