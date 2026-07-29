package com.quata.feature.auth.presentation.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.core.ui.components.PhoneInputSection
import com.quata.core.ui.components.QuataPrimaryButton
import com.quata.core.ui.components.QuataSecondaryButton
import com.quata.core.ui.components.QuataTextField

data class LoginFormStrings(
    val phone: String,
    val password: String,
    val signingIn: String,
    val signIn: String,
    val forgotPassword: String,
    val createAccount: String,
    val searchPrefix: String,
    val mockNotice: String,
)

@Composable
fun LoginForm(
    state: LoginUiState,
    prefixes: List<CountryPrefix>,
    strings: LoginFormStrings,
    isLandscape: Boolean,
    showMockNotice: Boolean,
    showRegistration: Boolean = true,
    phoneInputOverride: (@Composable (String, (String) -> Unit, Modifier) -> Unit)? = null,
    passwordInputOverride: (@Composable (String, (String) -> Unit, Modifier) -> Unit)? = null,
    phoneInputAccessibilityOverlay: (@Composable (String, (String) -> Unit, Modifier) -> Unit)? = null,
    passwordInputAccessibilityOverlay: (@Composable (String, (String) -> Unit, Modifier) -> Unit)? = null,
    submitButtonOverride: (@Composable (String, Boolean, () -> Unit, Modifier) -> Unit)? = null,
    onEvent: (LoginUiEvent) -> Unit,
    onForgotPassword: () -> Unit,
    onGoToRegister: () -> Unit,
) {
    val compactSpace = if (isLandscape) 6.dp else 8.dp
    phoneInputOverride?.invoke(state.phone, { onEvent(LoginUiEvent.PhoneChanged(it)) }, Modifier.fillMaxWidth().semantics { testTag = "auth.phone" })
        ?: PhoneInputSection(prefixes, state.countryCode, { onEvent(LoginUiEvent.CountryCodeChanged(it)) }, state.phone, { onEvent(LoginUiEvent.PhoneChanged(it)) }, strings.phone, strings.searchPrefix, Modifier.fillMaxWidth().semantics { testTag = "auth.phone" }, phoneInputAccessibilityOverlay)
    Spacer(Modifier.height(compactSpace))
    passwordInputOverride?.invoke(state.password, { onEvent(LoginUiEvent.PasswordChanged(it)) }, Modifier.fillMaxWidth().semantics { testTag = "auth.password" })
        ?: QuataTextField(state.password, { onEvent(LoginUiEvent.PasswordChanged(it)) }, strings.password, isPassword = true, modifier = Modifier.fillMaxWidth().semantics { testTag = "auth.password" }, inputOverlay = passwordInputAccessibilityOverlay)
    state.error?.let { Spacer(Modifier.height(compactSpace)); Text(it, color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(if (isLandscape) 10.dp else 14.dp))
    submitButtonOverride?.invoke(if (state.isLoading) strings.signingIn else strings.signIn, !state.isLoading, { onEvent(LoginUiEvent.Submit) }, Modifier.semantics { testTag = "auth.submit" })
        ?: QuataPrimaryButton(if (state.isLoading) strings.signingIn else strings.signIn, modifier = Modifier.semantics { testTag = "auth.submit" }, enabled = !state.isLoading) { onEvent(LoginUiEvent.Submit) }
    Spacer(Modifier.height(compactSpace))
    QuataSecondaryButton(strings.forgotPassword, modifier = Modifier.semantics { testTag = "auth.forgot-password" }, enabled = !state.isLoading, onClick = onForgotPassword)
    if (showRegistration) {
        Spacer(Modifier.height(compactSpace))
        QuataSecondaryButton(strings.createAccount, modifier = Modifier.semantics { testTag = "auth.register" }, onClick = onGoToRegister)
    }
    if (state.isLoading) { Spacer(Modifier.height(if (isLandscape) 8.dp else 12.dp)); CircularProgressIndicator() }
    if (showMockNotice) { Spacer(Modifier.height(if (isLandscape) 8.dp else 12.dp)); Text(strings.mockNotice, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium) }
}
