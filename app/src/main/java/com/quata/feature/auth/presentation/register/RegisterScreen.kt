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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.R
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataDropdownField
import com.quata.core.ui.components.QuataLogo
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.countryPrefixOptions
import com.quata.feature.profile.data.registrationSecretQuestionOptions

@Composable
fun RegisterScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val secretQuestions = remember(context) { context.registrationSecretQuestionOptions() }
    val selectedQuestionLabel = secretQuestions.firstOrNull { it.value == state.secretQuestion }?.label
        ?: secretQuestions.firstOrNull()?.label.orEmpty()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { if (it is RegisterEffect.Success) onRegisterSuccess() }
    }

    QuataScreen(padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuataLogo(subtitle = stringResource(R.string.auth_create_account_title))
            Spacer(Modifier.height(14.dp))
            QuataTextField(
                value = state.displayName,
                onValueChange = { viewModel.onEvent(RegisterUiEvent.DisplayNameChanged(it)) },
                label = stringResource(R.string.auth_name),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            QuataTextField(
                value = state.neighborhood,
                onValueChange = { viewModel.onEvent(RegisterUiEvent.NeighborhoodChanged(it)) },
                label = stringResource(R.string.auth_neighborhood_community),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PhoneInputSection(
                prefixes = prefixes,
                selectedPrefix = state.countryCode,
                onPrefixChange = { viewModel.onEvent(RegisterUiEvent.CountryCodeChanged(it)) },
                phone = state.phone,
                onPhoneChange = { viewModel.onEvent(RegisterUiEvent.PhoneChanged(it)) },
                phoneLabel = stringResource(R.string.auth_phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            QuataTextField(
                value = state.password,
                onValueChange = { viewModel.onEvent(RegisterUiEvent.PasswordChanged(it)) },
                label = stringResource(R.string.auth_password),
                modifier = Modifier.fillMaxWidth(),
                isPassword = true
            )
            Spacer(Modifier.height(8.dp))
            QuataDropdownField(
                value = state.secretQuestion,
                options = secretQuestions,
                optionLabel = { it.label },
                onSelected = { viewModel.onEvent(RegisterUiEvent.SecretQuestionChanged(it.value)) },
                displayText = selectedQuestionLabel,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            QuataTextField(
                value = state.secretAnswer,
                onValueChange = { viewModel.onEvent(RegisterUiEvent.SecretAnswerChanged(it)) },
                label = stringResource(R.string.auth_secret_answer),
                modifier = Modifier.fillMaxWidth()
            )
            if (state.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(14.dp))
            QuataPrimaryButton(
                text = if (state.isLoading) stringResource(R.string.auth_creating) else stringResource(R.string.auth_create_account),
                enabled = !state.isLoading
            ) {
                viewModel.onEvent(RegisterUiEvent.Submit)
            }
            Spacer(Modifier.height(8.dp))
            QuataSecondaryButton(stringResource(R.string.common_back), onClick = onBack)
        }
    }
}
