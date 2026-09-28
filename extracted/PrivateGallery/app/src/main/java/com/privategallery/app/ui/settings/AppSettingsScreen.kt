package com.privategallery.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onNavigateBack: () -> Unit,
    onOpenSecuritySettings: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(uiState.username) },
                supportingContent = { Text("Signed in") }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Security") },
                leadingContent = { Icon(Icons.Default.Security, contentDescription = null) },
                trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                modifier = Modifier.clickable { onOpenSecuritySettings() }
            )
            ListItem(
                headlineContent = { Text("Unlock with biometrics") },
                leadingContent = { Icon(Icons.Default.Fingerprint, contentDescription = null) },
                trailingContent = { Switch(checked = uiState.biometricLockEnabled, onCheckedChange = { viewModel.setBiometricLock(it) }) }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Log out") },
                leadingContent = { Icon(Icons.Default.Logout, contentDescription = null) },
                modifier = Modifier.clickable { viewModel.logout(onLoggedOut) }
            )
        }
    }
}
