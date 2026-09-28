package com.privategallery.app.ui.folder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.data.repository.AuthRepository
import com.privategallery.app.data.repository.FolderRepository
import com.privategallery.app.data.repository.MediaRepository
import com.privategallery.app.domain.model.ConnectionState
import com.privategallery.app.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FolderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val folderRepository: FolderRepository,
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val folderId: String = checkNotNull(savedStateHandle["folderId"])

    private val _uiState = MutableStateFlow(FolderUiState())
    val uiState: StateFlow<FolderUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { folderRepository.getAuthorizedFolder(folderId) }
                .onSuccess { folder -> _uiState.value = _uiState.value.copy(folder = folder) }
                .onFailure { _uiState.value = _uiState.value.copy(error = "You don't have access to this folder.") }
        }
        viewModelScope.launch {
            combine(
                mediaRepository.observePagedMedia(folderId, PAGE_SIZE, 0),
                syncEngine.connectionState
            ) { media, connMap ->
                val peerId = _uiState.value.folder?.participantUserId
                val connState = peerId?.let { connMap[it] } ?: ConnectionState.Offline
                _uiState.value.copy(isLoading = false, media = media, connectionState = connState)
            }.collect { _uiState.value = it }
        }
        refresh()
    }

    /** Backs the swipe-down pull-to-refresh gesture: checks inbound/outbound/deleted/membership/connection. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                folderRepository.refreshFromServer()
                val folder = folderRepository.getAuthorizedFolder(folderId)
                val currentUserId = authRepository.currentUser.value?.id
                syncEngine.syncFolder(folderId, currentUserId ?: "", folder.participantUserId)
                _uiState.value = _uiState.value.copy(folder = folder, error = null)
            } catch (e: java.io.IOException) {
                _uiState.value = _uiState.value.copy(error = "You're offline. Pull down to try again once you're back online.")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    fun addMedia(file: File, mimeType: String) {
        viewModelScope.launch {
            runCatching { mediaRepository.addMedia(folderId, file, mimeType) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun deleteMedia(mediaId: String) {
        viewModelScope.launch {
            runCatching { mediaRepository.deleteMedia(folderId, mediaId) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    companion object { private const val PAGE_SIZE = 60 }
}
