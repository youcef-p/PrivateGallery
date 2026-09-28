package com.privategallery.app.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.privategallery.app.data.local.entity.SyncEventEntity
import com.privategallery.app.ui.components.EmptyState
import com.privategallery.app.ui.components.SyncStatusBadge
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync status") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.padding(16.dp)) {
                SyncStatusBadge(state = uiState.connectionState)
            }
            HorizontalDivider()
            if (uiState.recentEvents.isEmpty()) {
                EmptyState(title = "No sync activity yet", subtitle = "Events will appear here as media is added, deleted, or synced.")
            } else {
                LazyColumn {
                    items(uiState.recentEvents, key = { it.id }) { event -> SyncEventRow(event) }
                }
            }
        }
    }
}

@Composable
private fun SyncEventRow(event: SyncEventEntity) {
    ListItem(
        headlineContent = { Text(event.eventType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) },
        supportingContent = { Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(event.timestamp))) },
        trailingContent = { Text(event.status.name, style = MaterialTheme.typography.labelLarge) }
    )
}
