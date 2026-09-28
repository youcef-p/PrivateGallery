package com.privategallery.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.data.repository.AuthRepository
import com.privategallery.app.data.repository.FolderRepository
import com.privategallery.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home only ever renders folders emitted by FolderRepository.observeMyFolders(), which is itself
 * backed by the access-control-gated DAO query (FolderDao.observeFoldersForUser). There is no
 * path in this ViewModel that can show a folder the current user isn't owner/participant of.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(folderRepository.observeMyFolders(), authRepository.currentUser) { folders, user ->
                val foldersWithUnsynced = folders.map { folder ->
                    folder.copy(unsyncedCount = runCatching { mediaRepository.countUnsynced(folder.id) }.getOrDefault(0))
                }
                HomeUiState(isLoading = false, folders = foldersWithUnsynced, currentUsername = user?.username ?: "")
            }.collect { _uiState.value = it }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                folderRepository.refreshFromServer()
                _uiState.value = _uiState.value.copy(isOffline = false, error = null)
            } catch (e: java.io.IOException) {
                _uiState.value = _uiState.value.copy(isOffline = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            runCatching { folderRepository.createFolder(name) }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }
}
