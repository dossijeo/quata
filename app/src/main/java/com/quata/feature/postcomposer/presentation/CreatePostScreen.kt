package com.quata.feature.postcomposer.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.core.designsystem.theme.QuataSurface
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.postcomposer.domain.PostComposerRepository

@Composable
fun CreatePostScreen(
    padding: PaddingValues,
    repository: PostComposerRepository,
    viewModel: CreatePostViewModel = viewModel(factory = CreatePostViewModel.factory(repository))
) {
    val state by viewModel.uiState.collectAsState()
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.onEvent(CreatePostUiEvent.ImageSelected(uri?.toString()))
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) viewModel.onEvent(CreatePostUiEvent.ImageSelected("camera-preview-bitmap"))
    }

    QuataScreen(padding) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Crear", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text("Publica texto, elige una foto o abre la cámara.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            QuataTextField(
                value = state.text,
                onValueChange = { viewModel.onEvent(CreatePostUiEvent.TextChanged(it)) },
                label = "¿Qué quieres compartir?",
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                minLines = 5
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(QuataSurface, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.imageUri ?: "Sin imagen seleccionada",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            QuataSecondaryButton(text = "Seleccionar de galería") {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            QuataSecondaryButton(text = "Abrir cámara") {
                cameraLauncher.launch(null)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.successMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(6.dp))
            QuataPrimaryButton(text = if (state.isLoading) "Publicando..." else "Publicar", enabled = !state.isLoading) {
                viewModel.onEvent(CreatePostUiEvent.Submit)
            }
        }
    }
}
