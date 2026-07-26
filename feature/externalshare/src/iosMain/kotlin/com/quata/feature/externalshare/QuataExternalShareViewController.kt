package com.quata.feature.externalshare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import platform.UIKit.UIViewController

/** iOS launcher input; payload parsing, file access, previewing and destination navigation stay host-owned. */
class IosExternalShareHostDependencies(
    val payload: ExternalSharePayload,
    val viewModel: ShareToQuataViewModel,
    val onDismiss: () -> Unit,
    val onOpenAttachment: (ExternalShareAttachment) -> Unit = {},
    val onOpenConversation: (String) -> Unit = {},
)

/** UIKit/Compose host for the common external-share state and destination selection flow. */
fun QuataExternalShareViewController(dependencies: IosExternalShareHostDependencies): UIViewController =
    ComposeUIViewController {
        QuataTheme {
            val state by dependencies.viewModel.uiState.collectAsState()
            DisposableEffect(dependencies.viewModel) { onDispose(dependencies.viewModel::close) }
            LaunchedEffect(state.isComplete, state.completedConversationId) {
                if (state.isComplete) {
                    state.completedConversationId?.let(dependencies.onOpenConversation)
                    dependencies.onDismiss()
                }
            }
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Compartir en Quata", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = dependencies.onDismiss,
                        enabled = !state.isSending,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Cancelar")
                    }
                    dependencies.payload.text.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
                    dependencies.payload.attachments.forEach { attachment ->
                        Button(onClick = { dependencies.onOpenAttachment(attachment) }) { Text(attachment.name) }
                    }
                    OutlinedTextField(
                        value = state.candidateQuery,
                        onValueChange = dependencies.viewModel::onQueryChanged,
                        label = { Text("Buscar destinos") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items((state.recentCandidates + state.candidates).distinctBy { it.profileId }) { candidate ->
                            Button(onClick = { dependencies.viewModel.toggle(candidate.profileId) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (candidate.profileId in state.selectedProfileIds) "✓ ${candidate.displayName}" else candidate.displayName)
                            }
                        }
                    }
                    Button(onClick = dependencies.viewModel::send, enabled = state.selectedProfileIds.isNotEmpty() && !state.isSending, modifier = Modifier.fillMaxWidth()) { Text("Enviar") }
                    if (state.isSending || state.error != null) {
                        ExternalShareSendingStateContent("Enviando…", state.isSending, state.error, "Cerrar", dependencies.onDismiss)
                    }
                }
            }
        }
    }
