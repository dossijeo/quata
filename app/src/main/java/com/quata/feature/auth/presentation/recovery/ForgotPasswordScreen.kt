package com.quata.feature.auth.presentation.recovery

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.quata.core.ui.components.QuataLogo
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataScreen
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.countryPrefixOptions
import com.quata.feature.profile.data.secretQuestionLabel

@Composable
fun ForgotPasswordScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    viewModel: ForgotPasswordViewModel = viewModel(factory = ForgotPasswordViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val secretQuestion = remember(context, state.secretQuestion, state.isLoadingQuestion) {
        when {
            state.isLoadingQuestion -> context.getString(R.string.auth_loading_secret_question)
            state.secretQuestion.isBlank() -> context.getString(R.string.auth_secret_question_waiting)
            else -> context.secretQuestionLabel(state.secretQuestion)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            if (effect is ForgotPasswordEffect.PasswordUpdated) {
                Toast.makeText(context, context.getString(R.string.auth_password_updated), Toast.LENGTH_SHORT).show()
                onBack()
            }
        }
    }

    QuataScreen(padding) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuataLogo(subtitle = stringResource(R.string.auth_recover_password_title))
            Spacer(Modifier.height(28.dp))
            PhoneInputSection(
                prefixes = prefixes,
                selectedPrefix = state.countryCode,
                onPrefixChange = { viewModel.onEvent(ForgotPasswordUiEvent.CountryCodeChanged(it)) },
                phone = state.phone,
                onPhoneChange = { viewModel.onEvent(ForgotPasswordUiEvent.PhoneChanged(it)) },
                phoneLabel = stringResource(R.string.auth_your_phone),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = secretQuestion,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.auth_your_secret_question)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.height(12.dp))
            QuataTextField(
                value = state.secretAnswer,
                onValueChange = { viewModel.onEvent(ForgotPasswordUiEvent.SecretAnswerChanged(it)) },
                label = stringResource(R.string.auth_your_secret_answer),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            QuataTextField(
                value = state.newPassword,
                onValueChange = { viewModel.onEvent(ForgotPasswordUiEvent.NewPasswordChanged(it)) },
                label = stringResource(R.string.auth_new_password),
                modifier = Modifier.fillMaxWidth(),
                isPassword = true
            )
            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(22.dp))
            QuataPrimaryButton(
                text = if (state.isUpdating) stringResource(R.string.common_saving) else stringResource(R.string.auth_update_password),
                enabled = !state.isUpdating
            ) {
                viewModel.onEvent(ForgotPasswordUiEvent.Submit)
            }
            Spacer(Modifier.height(10.dp))
            QuataSecondaryButton(stringResource(R.string.common_back), onClick = onBack)
        }
    }
}
