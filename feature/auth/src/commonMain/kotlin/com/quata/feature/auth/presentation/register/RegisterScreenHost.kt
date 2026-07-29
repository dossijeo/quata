package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.RegisterRepository
import com.quata.feature.auth.presentation.AuthCatalogCopy
import com.quata.feature.auth.presentation.AuthScreenLayoutContent

/** Product registration screen shared verbatim by Android and browser launchers. */
@Composable
fun RegisterScreenHost(
    padding: PaddingValues,
    repository: RegisterRepository,
    catalog: AuthCatalogCopy,
    prefixes: List<CountryPrefix>,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
) {
    val viewModel = remember(repository) { RegisterViewModel(repository) }
    val state by viewModel.uiState.collectAsState()

    DisposableEffect(viewModel) {
        onDispose(viewModel::close)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is RegisterEffect.Success) onRegisterSuccess() }
    }

    AuthScreenLayoutContent(
        padding = padding,
        subtitle = catalog.register.title,
        portraitLogoSpacing = 14.dp,
    ) { isLandscape ->
        RegisterForm(
            state = state,
            prefixes = prefixes,
            secretQuestions = catalog.secretQuestions,
            strings = RegisterFormStrings(
                displayName = catalog.register.displayName,
                neighborhood = catalog.register.neighborhood,
                phone = catalog.login.phone,
                password = catalog.login.password,
                secretAnswer = catalog.register.secretAnswer,
                searchPrefix = catalog.login.searchPrefix,
                creating = catalog.register.creating,
                createAccount = catalog.register.createAccount,
                back = catalog.register.back,
            ),
            isLandscape = isLandscape,
            onEvent = viewModel::onEvent,
            onBack = onBack,
        )
    }
}
