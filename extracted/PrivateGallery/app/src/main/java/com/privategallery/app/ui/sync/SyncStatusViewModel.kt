package com.privategallery.app.ui.sync

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privategallery.app.data.local.dao.SyncEventDao
import com.privategallery.app.data.local.entity.SyncEventEntity
import com.privategallery.app.domain.model.ConnectionState
import com.privategallery.app.sync.SyncEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SyncStatusUiState(
    val connectionState: ConnectionState = ConnectionState.Offline,
    val recentEvents: List<SyncEventEntity> = emptyList()
)

@HiltViewModel
class SyncStatusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val syncEventDao: SyncEventDao,
    private val syncEngine: SyncEngine
) : ViewModel() {

    private val folderId: String = checkNotNull(savedStateHandle["folderId"])
    private val _uiState = MutableStateFlow(SyncStatusUiState())
    val uiState: StateFlow<SyncStatusUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(syncEventDao.observeRecent(folderId), syncEngine.connectionState) { events, connMap ->
                // Sync status here is shown per-folder aggregate; the specific peer's state is
                // whichever entry the folder's participant maps to (resolved by FolderViewModel
                // elsewhere) — this screen shows all known peer states for transparency.
                SyncStatusUiState(
                    connectionState = connMap.values.firstOrNull() ?: ConnectionState.Offline,
                    recentEvents = events
                )
            }.collect { _uiState.value = it }
        }
    }
}
