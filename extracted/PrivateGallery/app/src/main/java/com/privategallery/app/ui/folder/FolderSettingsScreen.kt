package com.privategallery.app.ui.folder

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSettingsScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    onFolderDeleted: () -> Unit,
    viewModel: FolderSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var name by remember(uiState.folderName) { mutableStateOf(uiState.folderName) }
    var showRemoveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folder settings") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Folder name") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { TextButton(onClick = { viewModel.rename(name) }) { Text("Save") } }
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text("Shared with", style = MaterialTheme.typography.titleMedium)
            Text(uiState.participantName ?: "No one yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (uiState.participantName != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { showRemoveConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Remove partner from this folder")
                }
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete folder")
            }

            uiState.error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove partner?") },
            text = { Text("They'll lose access immediately. Media they've already received stays on their device — this only prevents anything new from being shared with them.") },
            confirmButton = { TextButton(onClick = { viewModel.removeParticipant(); showRemoveConfirm = false }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") } }
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this folder?") },
            text = { Text("This permanently deletes it and its media on this device and removes the server's reference to it.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteFolder(onFolderDeleted); showDeleteConfirm = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
