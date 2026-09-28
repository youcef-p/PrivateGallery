package com.privategallery.app.ui.invite

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteUserScreen(
    folderId: String,
    onNavigateBack: () -> Unit,
    viewModel: InviteViewModel = hiltViewModel()
) {
    var identifier by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(InviteMethod.USERNAME) }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite your partner") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text(
                "This folder is only ever shared with exactly one other person. They'll need to " +
                    "accept before any media starts syncing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                InviteMethod.entries.forEachIndexed { index, method ->
                    SegmentedButton(
                        selected = selectedMethod == method,
                        onClick = { selectedMethod = method },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = InviteMethod.entries.size)
                    ) { Text(method.name) }
                }
            }
            Spacer(Modifier.height(20.dp))

            when (selectedMethod) {
                InviteMethod.QR -> Text("QR scanning UI goes here — shares the same createInvite(code) call below once a code is scanned.")
                else -> OutlinedTextField(
                    value = identifier, onValueChange = { identifier = it },
                    label = { Text(if (selectedMethod == InviteMethod.EMAIL) "Partner's email" else "Partner's username") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }

            when (val state = uiState) {
                is InviteUiState.Created -> {
                    Spacer(Modifier.height(20.dp))
                    Text("Invite created. Share this code:", style = MaterialTheme.typography.bodyMedium)
                    Text(state.inviteCode, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace)
                }
                is InviteUiState.Error -> {
                    Spacer(Modifier.height(12.dp))
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { viewModel.createInvite(identifier, selectedMethod) },
                enabled = uiState !is InviteUiState.Loading && (selectedMethod == InviteMethod.QR || identifier.isNotBlank()),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (uiState is InviteUiState.Loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Send invite")
            }
        }
    }
}
