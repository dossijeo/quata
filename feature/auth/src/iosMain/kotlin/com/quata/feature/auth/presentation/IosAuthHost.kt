package com.quata.feature.auth.presentation

import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.presentation.login.LoginFormStrings
import platform.UIKit.UIViewController

/**
 * iOS composition boundary for the shared Auth login form.
 *
 * The launcher supplies its authenticated repository, locale strings, country prefixes and
 * post-login session handling. This module owns no endpoint, credential, sample account or
 * platform sign-in SDK.
 */
class IosAuthHostDependencies(
    val repository: AuthRepository,
    val prefixes: List<CountryPrefix>,
    val strings: LoginFormStrings,
    val subtitle: String,
    val recoveryUnavailableMessage: String,
    val registerUnavailableMessage: String,
    val onLoginSuccess: suspend () -> Unit,
)

/** Stable Swift-exported UIViewController factory backed by common Auth ViewModels and Compose. */
fun QuataAuthViewController(dependencies: IosAuthHostDependencies): UIViewController = ComposeUIViewController {
    QuataTheme {
        AuthBrowserLoginHostContent(
            repository = dependencies.repository,
            prefixes = dependencies.prefixes,
            strings = dependencies.strings,
            subtitle = dependencies.subtitle,
            recoveryUnavailableMessage = dependencies.recoveryUnavailableMessage,
            registerUnavailableMessage = dependencies.registerUnavailableMessage,
            onLoginSuccess = dependencies.onLoginSuccess,
        )
    }
}
