package com.quata.feature.auth.presentation.recovery

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.CountryPrefix
import com.quata.core.ui.components.CompactTextFieldHeight
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField

data class ForgotPasswordFormStrings(val phone: String, val searchPrefix: String, val secretQuestion: String, val secretAnswer: String, val newPassword: String, val saving: String, val updatePassword: String, val back: String)

@Composable fun ForgotPasswordForm(state: ForgotPasswordUiState, prefixes: List<CountryPrefix>, resolvedQuestion: String, strings: ForgotPasswordFormStrings, isLandscape: Boolean, onEvent: (ForgotPasswordUiEvent) -> Unit, onBack: () -> Unit) {
    val space = if (isLandscape) 6.dp else 8.dp; val template = quataTheme()
    PhoneInputSection(prefixes, state.countryCode, { onEvent(ForgotPasswordUiEvent.CountryCodeChanged(it)) }, state.phone, { onEvent(ForgotPasswordUiEvent.PhoneChanged(it)) }, strings.phone, strings.searchPrefix, Modifier.fillMaxWidth())
    Spacer(Modifier.height(space))
    OutlinedTextField(resolvedQuestion, {}, readOnly = true, placeholder = { Text(strings.secretQuestion) }, modifier = Modifier.fillMaxWidth().height(CompactTextFieldHeight), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = template.colors.surfaceAlt, unfocusedContainerColor = template.colors.surfaceAlt, focusedBorderColor = template.colors.accent, unfocusedBorderColor = template.colors.inputBorder, cursorColor = template.colors.accent))
    Spacer(Modifier.height(space)); QuataTextField(state.secretAnswer, { onEvent(ForgotPasswordUiEvent.SecretAnswerChanged(it)) }, strings.secretAnswer, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(space)); QuataTextField(state.newPassword, { onEvent(ForgotPasswordUiEvent.NewPasswordChanged(it)) }, strings.newPassword, modifier = Modifier.fillMaxWidth(), isPassword = true)
    state.error?.let { Spacer(Modifier.height(space)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(if (isLandscape) 10.dp else 14.dp)); QuataPrimaryButton(if (state.isUpdating) strings.saving else strings.updatePassword, enabled = !state.isUpdating) { onEvent(ForgotPasswordUiEvent.Submit) }
    Spacer(Modifier.height(space)); QuataSecondaryButton(strings.back, onClick = onBack)
}
