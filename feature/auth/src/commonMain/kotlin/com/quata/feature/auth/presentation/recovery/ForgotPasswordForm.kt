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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.model.CountryPrefix
import com.quata.core.ui.components.CompactTextFieldHeight
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField

data class ForgotPasswordFormStrings(
    val phone: String,
    val searchPrefix: String,
    val secretQuestion: String,
    val secretAnswer: String,
    val newPassword: String,
    val saving: String,
    val updatePassword: String,
    val back: String,
)

@Composable
fun ForgotPasswordForm(
    state: ForgotPasswordUiState,
    prefixes: List<CountryPrefix>,
    resolvedQuestion: String,
    strings: ForgotPasswordFormStrings,
    isLandscape: Boolean,
    onEvent: (ForgotPasswordUiEvent) -> Unit,
    onBack: () -> Unit,
) {
    val space = if (isLandscape) 6.dp else 8.dp
    val template = quataTheme()
    PhoneInputSection(
        prefixes = prefixes,
        selectedPrefix = state.countryCode,
        onPrefixChange = { onEvent(ForgotPasswordUiEvent.CountryCodeChanged(it)) },
        phone = state.phone,
        onPhoneChange = { onEvent(ForgotPasswordUiEvent.PhoneChanged(it)) },
        phoneLabel = strings.phone,
        searchPlaceholder = strings.searchPrefix,
        modifier = Modifier.fillMaxWidth(),
        prefixTestTag = ForgotPasswordTestTags.CountryPrefix,
        phoneTestTag = ForgotPasswordTestTags.Phone,
    )
    Spacer(Modifier.height(space))
    OutlinedTextField(
        value = resolvedQuestion,
        onValueChange = {},
        readOnly = true,
        placeholder = { Text(strings.secretQuestion) },
        modifier = Modifier
            .fillMaxWidth()
            .height(CompactTextFieldHeight)
            .semantics { testTag = ForgotPasswordTestTags.Question },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = template.colors.surfaceAlt,
            unfocusedContainerColor = template.colors.surfaceAlt,
            focusedBorderColor = template.colors.accent,
            unfocusedBorderColor = template.colors.inputBorder,
            cursorColor = template.colors.accent,
        ),
    )
    Spacer(Modifier.height(space))
    QuataTextField(
        value = state.secretAnswer,
        onValueChange = { onEvent(ForgotPasswordUiEvent.SecretAnswerChanged(it)) },
        label = strings.secretAnswer,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = ForgotPasswordTestTags.SecretAnswer },
    )
    Spacer(Modifier.height(space))
    QuataTextField(
        value = state.newPassword,
        onValueChange = { onEvent(ForgotPasswordUiEvent.NewPasswordChanged(it)) },
        label = strings.newPassword,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { testTag = ForgotPasswordTestTags.NewPassword },
        isPassword = true,
    )
    state.error?.let {
        Spacer(Modifier.height(space))
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { testTag = ForgotPasswordTestTags.Error },
        )
    }
    Spacer(Modifier.height(if (isLandscape) 10.dp else 14.dp))
    QuataPrimaryButton(
        text = if (state.isUpdating) strings.saving else strings.updatePassword,
        modifier = Modifier.semantics { testTag = ForgotPasswordTestTags.Submit },
        enabled = !state.isUpdating,
    ) { onEvent(ForgotPasswordUiEvent.Submit) }
    Spacer(Modifier.height(space))
    QuataSecondaryButton(
        text = strings.back,
        modifier = Modifier.semantics { testTag = ForgotPasswordTestTags.Back },
        onClick = onBack,
    )
}
