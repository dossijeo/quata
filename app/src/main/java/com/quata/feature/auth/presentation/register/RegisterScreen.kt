package com.quata.feature.auth.presentation.register

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.quata.core.localization.QuataLanguageManager
import com.quata.core.moderation.LegalDocument
import com.quata.core.ui.components.QuataLegalDocumentLinksContent
import com.quata.feature.auth.domain.AuthRepository
import com.quata.feature.profile.data.authCatalog
import com.quata.feature.profile.data.countryPrefixOptions
import kotlinx.coroutines.launch

/** Android launcher for the shared registration hierarchy. */
@Composable
fun RegisterScreen(
    padding: PaddingValues,
    authRepository: AuthRepository,
    openLegalDocument: suspend (LegalDocument) -> Unit = {},
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val prefixes = remember(context) { context.countryPrefixOptions() }
    val catalog = remember(context) { context.authCatalog() }
    val scope = rememberCoroutineScope()
    RegisterScreenHost(
        padding = padding,
        repository = authRepository,
        catalog = catalog,
        prefixes = prefixes,
        legalLinks = {
            QuataLegalDocumentLinksContent(
                language = QuataLanguageManager.currentLanguage,
                onOpenDocument = { document -> scope.launch { openLegalDocument(document) } },
            )
        },
        onBack = onBack,
        onRegisterSuccess = onRegisterSuccess,
    )
}
