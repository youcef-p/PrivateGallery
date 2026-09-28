package com.privategallery.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecuritySettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SecuritySettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text("Block screenshots") },
                supportingContent = { Text("Prevents screenshots and hides content in the app switcher") },
                trailingContent = {
                    Switch(checked = uiState.screenshotProtectionEnabled, onCheckedChange = { viewModel.setScreenshotProtection(it) })
                }
            )
            ListItem(
                headlineContent = { Text("Require biometrics to open app") },
                trailingContent = {
                    Switch(checked = uiState.biometricLockEnabled, onCheckedChange = { viewModel.setBiometricLock(it) })
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Clear decrypted cache") },
                supportingContent = { Text("Removes any temporarily decrypted preview files from this device. Your encrypted library is untouched.") },
                modifier = Modifier.clickable { viewModel.clearDecryptedCache(context.cacheDir) }
            )
        }
    }
}
