package com.quata.feature.auth.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.core.ui.components.QuataLogo
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.auth.domain.AuthRepository

@Composable
fun LoginScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onGoToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is LoginEffect.Success) onLoginSuccess()
        }
    }

    QuataScreen(padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuataLogo(subtitle = "Conecta, publica y conversa")
            Spacer(Modifier.height(36.dp))
            QuataTextField(
                value = state.email,
                onValueChange = { viewModel.onEvent(LoginUiEvent.EmailChanged(it)) },
                label = "Email",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            QuataTextField(
                value = state.password,
                onValueChange = { viewModel.onEvent(LoginUiEvent.PasswordChanged(it)) },
                label = "Contraseña",
                isPassword = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(22.dp))
            QuataPrimaryButton(
                text = if (state.isLoading) "Entrando..." else "Entrar",
                enabled = !state.isLoading
            ) { viewModel.onEvent(LoginUiEvent.Submit) }
            Spacer(Modifier.height(10.dp))
            QuataSecondaryButton(
                text = "Continuar con Google",
                enabled = !state.isLoading
            ) { viewModel.onEvent(LoginUiEvent.GoogleSubmit(context)) }
            Spacer(Modifier.height(16.dp))
            QuataSecondaryButton(text = "Crear cuenta", onClick = onGoToRegister)
            if (state.isLoading) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator()
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Modo mock activo por defecto. Configura AppConfig para backend real.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
