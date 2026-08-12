package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import com.quata.core.localization.QuataLanguage
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.PreferenceStore
import com.quata.core.ui.components.QuataLegalDocumentLinksContent
import com.quata.feature.auth.presentation.AuthCatalog
import com.quata.feature.auth.presentation.AuthCatalogLocale
import com.quata.feature.auth.presentation.AuthProductDestination
import com.quata.feature.auth.presentation.AuthProductHostContent
import kotlinx.coroutines.launch

/**
 * Web keeps only its session hand-off here; the rendered Auth hierarchy is the same common
 * product root consumed by Android.
 */
@Composable
fun WebLoginHost(
    repository: WebAuthRepository,
    preferences: PreferenceStore,
    documentOpener: DocumentOpenService,
    initialDestination: AuthProductDestination = AuthProductDestination.Login,
    onLoginSuccess: () -> Unit,
) {
    val catalog = AuthCatalog.copy(AuthCatalogLocale.Spanish)
    val scope = rememberCoroutineScope()
    AuthProductHostContent(
        repository = repository,
        catalog = catalog,
        prefixes = AuthCatalog.countryPrefixes(AuthCatalogLocale.Spanish),
        initialDestination = initialDestination,
        registerLegalLinks = {
            QuataLegalDocumentLinksContent(
                language = QuataLanguage.Spanish,
                onOpenDocument = { document ->
                    scope.launch { documentOpener.open(webLegalDocumentFile(document, QuataLanguage.Spanish)) }
                },
            )
        },
        onAuthenticated = {
            scope.launch {
                preferences.putString(WebSessionReadyKey, "true")
                onLoginSuccess()
            }
        },
    )
}

internal const val WebSessionReadyKey = "web.auth.session_ready"
