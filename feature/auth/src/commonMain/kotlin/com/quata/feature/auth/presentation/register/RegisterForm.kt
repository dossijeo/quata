package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataDropdownField
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField

data class RegisterSecretQuestion(val value: String, val label: String)

object RegisterTestTags {
    const val DisplayName = "auth.register.display-name"
    const val Neighborhood = "auth.register.neighborhood"
    const val Phone = "auth.register.phone"
    const val CountryPrefix = "auth.register.country-prefix"
    const val PhoneInput = "auth.register.phone.input"
    const val Password = "auth.register.password"
    const val SecretQuestion = "auth.register.secret-question"
    const val SecretAnswer = "auth.register.secret-answer"
    const val Error = "auth.register.error"
    const val Submit = "auth.register.submit"
    const val Back = "auth.register.back"
}

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
    legalLinks: @Composable (() -> Unit)? = null,
) {
    val space = if (isLandscape) 6.dp else 8.dp
    val selectedQuestionLabel = secretQuestions.firstOrNull { it.value == state.secretQuestion }?.label
        ?: secretQuestions.firstOrNull()?.label.orEmpty()
    QuataTextField(state.displayName, { onEvent(RegisterUiEvent.DisplayNameChanged(it)) }, strings.displayName, modifier = Modifier.fillMaxWidth().semantics { testTag = RegisterTestTags.DisplayName })
    Spacer(Modifier.height(space))
    QuataTextField(state.neighborhood, { onEvent(RegisterUiEvent.NeighborhoodChanged(it)) }, strings.neighborhood, modifier = Modifier.fillMaxWidth().semantics { testTag = RegisterTestTags.Neighborhood })
    Spacer(Modifier.height(space))
    PhoneInputSection(
        prefixes,
        state.countryCode,
        { onEvent(RegisterUiEvent.CountryCodeChanged(it)) },
        state.phone,
        { onEvent(RegisterUiEvent.PhoneChanged(it)) },
        strings.phone,
        strings.searchPrefix,
        Modifier.fillMaxWidth().semantics { testTag = RegisterTestTags.Phone },
        prefixTestTag = RegisterTestTags.CountryPrefix,
        phoneTestTag = RegisterTestTags.PhoneInput,
    )
    Spacer(Modifier.height(space))
    QuataTextField(state.password, { onEvent(RegisterUiEvent.PasswordChanged(it)) }, strings.password, modifier = Modifier.fillMaxWidth().semantics { testTag = RegisterTestTags.Password }, isPassword = true)
    Spacer(Modifier.height(space))
    QuataDropdownField(state.secretQuestion, secretQuestions, { it.label }, { onEvent(RegisterUiEvent.SecretQuestionChanged(it.value)) }, selectedQuestionLabel, Modifier.fillMaxWidth().semantics { testTag = RegisterTestTags.SecretQuestion })
    Spacer(Modifier.height(space))
    QuataTextField(state.secretAnswer, { onEvent(RegisterUiEvent.SecretAnswerChanged(it)) }, strings.secretAnswer, modifier = Modifier.fillMaxWidth().semantics { testTag = RegisterTestTags.SecretAnswer })
    state.error?.let { Spacer(Modifier.height(space)); Text(it, modifier = Modifier.semantics { testTag = RegisterTestTags.Error }, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(if (isLandscape) 10.dp else 14.dp))
    QuataPrimaryButton(if (state.isLoading) strings.creating else strings.createAccount, modifier = Modifier.semantics { testTag = RegisterTestTags.Submit }, enabled = !state.isLoading) { onEvent(RegisterUiEvent.Submit) }
    Spacer(Modifier.height(space))
    QuataSecondaryButton(strings.back, modifier = Modifier.semantics { testTag = RegisterTestTags.Back }, onClick = onBack)
    legalLinks?.let {
        Spacer(Modifier.height(if (isLandscape) 10.dp else 14.dp))
        it()
    }
}
