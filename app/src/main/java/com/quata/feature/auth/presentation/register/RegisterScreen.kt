package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.auth.presentation.AuthScreenLayoutContent
import com.quata.feature.auth.presentation.register.RegisterForm
import com.quata.feature.auth.presentation.register.RegisterFormStrings
import com.quata.feature.auth.presentation.register.RegisterSecretQuestion
import com.quata.feature.profile.data.countryPrefixOptions
import com.quata.feature.profile.data.authCatalog
import com.quata.feature.profile.data.registrationSecretQuestionOptions

@Composable
fun RegisterScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterAndroidViewModel = viewModel(factory = RegisterAndroidViewModel.factory(authRepository))
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val secretQuestions = remember(context) { context.registrationSecretQuestionOptions() }
    val catalog = remember(context) { context.authCatalog() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { if (it is RegisterEffect.Success) onRegisterSuccess() }
    }

    AuthScreenLayoutContent(
        padding = padding,
        subtitle = catalog.register.title,
        portraitLogoSpacing = 14.dp
    ) { isLandscape ->
        RegisterForm(
            state = state,
            prefixes = prefixes,
            secretQuestions = secretQuestions.map { RegisterSecretQuestion(it.value, it.label) },
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
