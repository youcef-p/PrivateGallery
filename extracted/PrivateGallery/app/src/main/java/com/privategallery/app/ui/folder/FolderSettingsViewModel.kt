package com.privategallery.app.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.data.repository.FolderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderSettingsUiState(
    val folderName: String = "",
    val participantName: String? = null,
    val isDeleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class FolderSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository
) : ViewModel() {

    private val folderId: String = checkNotNull(savedStateHandle["folderId"])
    private val _uiState = MutableStateFlow(FolderSettingsUiState())
    val uiState: StateFlow<FolderSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { folderRepository.getAuthorizedFolder(folderId) }
                .onSuccess { _uiState.value = _uiState.value.copy(folderName = it.name, participantName = it.participantDisplayName) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            runCatching { folderRepository.renameFolder(folderId, newName) }
                .onSuccess { _uiState.value = _uiState.value.copy(folderName = newName) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    /** Removing the participant revokes their access immediately and rotates the folder key
     *  (see FolderRepositoryImpl.removeParticipant / docs/SECURITY.md § Revocation). */
    fun removeParticipant() {
        viewModelScope.launch {
            runCatching { folderRepository.removeParticipant(folderId) }
                .onSuccess { _uiState.value = _uiState.value.copy(participantName = null) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun deleteFolder(onDone: () -> Unit) {
        viewModelScope.launch {
            runCatching { folderRepository.deleteFolder(folderId) }
                .onSuccess { _uiState.value = _uiState.value.copy(isDeleted = true); onDone() }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }
}
