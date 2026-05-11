package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.ui.components.QuataLogo
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.auth.domain.AuthRepository

@Composable
fun RegisterScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { if (it is RegisterEffect.Success) onRegisterSuccess() }
    }

    QuataScreen(padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuataLogo(subtitle = stringResource(R.string.auth_create_account_title))
            Spacer(Modifier.height(28.dp))
            QuataTextField(state.displayName, { viewModel.onEvent(RegisterUiEvent.DisplayNameChanged(it)) }, stringResource(R.string.auth_name), Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            QuataTextField(state.email, { viewModel.onEvent(RegisterUiEvent.EmailChanged(it)) }, stringResource(R.string.auth_email), Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            QuataTextField(state.password, { viewModel.onEvent(RegisterUiEvent.PasswordChanged(it)) }, stringResource(R.string.auth_password), Modifier.fillMaxWidth(), isPassword = true)
            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(22.dp))
            QuataPrimaryButton(if (state.isLoading) stringResource(R.string.auth_creating) else stringResource(R.string.auth_create_account), enabled = !state.isLoading) {
                viewModel.onEvent(RegisterUiEvent.Submit)
            }
            Spacer(Modifier.height(10.dp))
            QuataSecondaryButton(stringResource(R.string.common_back), onClick = onBack)
        }
    }
}
