package com.quata.feature.auth.presentation.recovery

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.quata.core.model.CountryPrefix
import com.quata.feature.auth.domain.PasswordRecoveryRepository
import com.quata.feature.auth.presentation.AuthCatalogCopy
import com.quata.feature.auth.presentation.AuthScreenLayoutContent

/** Shared recovery root. Hosts own platform feedback and navigation only. */
@Composable
fun ForgotPasswordScreenHost(
    padding: PaddingValues,
    repository: PasswordRecoveryRepository,
    catalog: AuthCatalogCopy,
    prefixes: List<CountryPrefix>,
    onBack: () -> Unit,
    onPasswordUpdated: () -> Unit,
) {
    val viewModel = remember(repository) { ForgotPasswordViewModel(repository) }
    val state by viewModel.uiState.collectAsState()
    val resolvedQuestion = when {
        state.isLoadingQuestion -> catalog.recoveryQuestionLoading
        state.secretQuestion.isBlank() -> catalog.recoveryQuestionWaiting
        else -> catalog.secretQuestions.firstOrNull { it.value == state.secretQuestion }?.label
            ?: state.secretQuestion
    }

    DisposableEffect(viewModel) { onDispose(viewModel::close) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it is ForgotPasswordEffect.PasswordUpdated) onPasswordUpdated() }
    }

    AuthScreenLayoutContent(
        padding = padding,
        subtitle = catalog.recoverySubtitle,
        portraitLogoSpacing = 14.dp,
    ) { isLandscape ->
        Column(
            modifier = Modifier.semantics { testTag = ForgotPasswordTestTags.Root },
        ) {
            ForgotPasswordForm(
                state = state,
                prefixes = prefixes,
                resolvedQuestion = resolvedQuestion,
                strings = catalog.recovery,
                isLandscape = isLandscape,
                onEvent = viewModel::onEvent,
                onBack = onBack,
            )
        }
    }
}
