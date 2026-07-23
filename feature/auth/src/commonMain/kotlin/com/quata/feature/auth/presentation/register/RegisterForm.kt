package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataDropdownField
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField

data class RegisterSecretQuestion(val value: String, val label: String)

data class RegisterFormStrings(
    val displayName: String,
    val neighborhood: String,
    val phone: String,
    val password: String,
    val secretAnswer: String,
    val searchPrefix: String,
    val creating: String,
    val createAccount: String,
    val back: String,
)

@Composable
fun RegisterForm(
    state: RegisterUiState,
    prefixes: List<CountryPrefix>,
    secretQuestions: List<RegisterSecretQuestion>,
    strings: RegisterFormStrings,
    isLandscape: Boolean,
    onEvent: (RegisterUiEvent) -> Unit,
    onBack: () -> Unit,
) {
    val space = if (isLandscape) 6.dp else 8.dp
    val selectedQuestionLabel = secretQuestions.firstOrNull { it.value == state.secretQuestion }?.label
        ?: secretQuestions.firstOrNull()?.label.orEmpty()
    QuataTextField(state.displayName, { onEvent(RegisterUiEvent.DisplayNameChanged(it)) }, strings.displayName, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(space))
    QuataTextField(state.neighborhood, { onEvent(RegisterUiEvent.NeighborhoodChanged(it)) }, strings.neighborhood, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(space))
    PhoneInputSection(prefixes, state.countryCode, { onEvent(RegisterUiEvent.CountryCodeChanged(it)) }, state.phone, { onEvent(RegisterUiEvent.PhoneChanged(it)) }, strings.phone, strings.searchPrefix, Modifier.fillMaxWidth())
    Spacer(Modifier.height(space))
    QuataTextField(state.password, { onEvent(RegisterUiEvent.PasswordChanged(it)) }, strings.password, modifier = Modifier.fillMaxWidth(), isPassword = true)
    Spacer(Modifier.height(space))
    QuataDropdownField(state.secretQuestion, secretQuestions, { it.label }, { onEvent(RegisterUiEvent.SecretQuestionChanged(it.value)) }, selectedQuestionLabel, Modifier.fillMaxWidth())
    Spacer(Modifier.height(space))
    QuataTextField(state.secretAnswer, { onEvent(RegisterUiEvent.SecretAnswerChanged(it)) }, strings.secretAnswer, modifier = Modifier.fillMaxWidth())
    state.error?.let { Spacer(Modifier.height(space)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(if (isLandscape) 10.dp else 14.dp))
    QuataPrimaryButton(if (state.isLoading) strings.creating else strings.createAccount, enabled = !state.isLoading) { onEvent(RegisterUiEvent.Submit) }
    Spacer(Modifier.height(space))
    QuataSecondaryButton(strings.back, onClick = onBack)
}
