package com.quata.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentViewerState
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.documentViewerOpeningState
import com.quata.core.platform.openWithViewerState
import com.quata.core.ui.components.QuataDocumentViewerStatusContent
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
    var documentViewerState by remember { mutableStateOf<DocumentViewerState?>(null) }
    DisposableEffect(Unit) {
        val uninstallStatus = installWebDocumentStatusE2eBridge("auth") {
            documentViewerState = null
        }
        val uninstallLegal = installWebLegalDocumentsE2eBridge(
            surface = "auth",
            openPrivacy = {
                scope.launch {
                    val file = webLegalDocumentFile(LegalDocument.Privacy, QuataLanguage.Spanish)
                    documentViewerState = documentViewerOpeningState(file)
                    documentViewerState = documentOpener.openWithViewerState(file).completed
                }
            },
            openChildSafety = {
                scope.launch {
                    val file = webLegalDocumentFile(LegalDocument.ChildSafety, QuataLanguage.Spanish)
                    documentViewerState = documentViewerOpeningState(file)
                    documentViewerState = documentOpener.openWithViewerState(file).completed
                }
            },
            dismissStatus = { documentViewerState = null },
        )
        onDispose {
            uninstallStatus()
            uninstallLegal()
        }
    }
    AuthProductHostContent(
        repository = repository,
        catalog = catalog,
        prefixes = AuthCatalog.countryPrefixes(AuthCatalogLocale.Spanish),
        initialDestination = initialDestination,
        registerLegalLinks = {
            WebNativeLegalDocumentLinksContent(
                language = QuataLanguage.Spanish,
                onOpenDocument = { document ->
                    scope.launch {
                        val file = webLegalDocumentFile(document, QuataLanguage.Spanish)
                        documentViewerState = documentViewerOpeningState(file)
                        documentViewerState = documentOpener.openWithViewerState(file).completed
                    }
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
    QuataDocumentViewerStatusContent(
        state = documentViewerState,
        strings = webDocumentViewerStatusStrings(listOf("es")),
        onDismiss = { documentViewerState = null },
    )
}

internal const val WebSessionReadyKey = "web.auth.session_ready"
