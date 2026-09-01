package com.quata.feature.auth.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.LocalFirstUgcTermsGateway
import com.quata.core.moderation.PreferenceUgcTermsAcceptanceStore
import com.quata.core.moderation.UgcTermsAcceptanceGateway
import com.quata.core.moderation.UgcTermsGateway
import com.quata.core.moderation.UgcTermsRemoteGateway
import com.quata.core.moderation.iosLegalDocumentFile
import com.quata.core.moderation.iosLegalDocumentPlaceholderFile
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentViewerFailureReason
import com.quata.core.platform.DocumentViewerState
import com.quata.core.platform.PreferenceStore
import com.quata.core.platform.documentViewerOpeningState
import com.quata.core.platform.openWithViewerState
import com.quata.core.session.IosRenewableAuthSession
import com.quata.core.ui.components.QuataDocumentViewerStatusContent
import com.quata.core.ui.components.QuataLegalDocumentLinksColumnContent
import com.quata.core.ui.components.QuataUgcTermsGateContent
import com.quata.core.ui.components.quataDocumentViewerStatusStrings
import com.quata.core.ui.components.quataUgcTermsStrings
import com.quata.feature.auth.data.IosAuthHttpTransport
import com.quata.feature.auth.data.IosAuthRuntimeConfiguration
import com.quata.feature.auth.data.IosUrlSessionAuthHttpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import platform.UIKit.UIViewController

fun createIosUgcTermsGateway(
    configuration: IosAuthRuntimeConfiguration,
    session: IosRenewableAuthSession,
    preferences: PreferenceStore,
): UgcTermsGateway = createIosUgcTermsGateway(
    configuration = configuration,
    session = session,
    preferences = preferences,
    transport = IosUrlSessionAuthHttpTransport(),
)

fun createIosUgcTermsGateway(
    configuration: IosAuthRuntimeConfiguration,
    session: IosRenewableAuthSession,
    preferences: PreferenceStore,
    transport: IosAuthHttpTransport,
): UgcTermsGateway {
    val pendingSyncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return LocalFirstUgcTermsGateway(
        profileIdProvider = { session.restoredSession()?.userId },
        store = PreferenceUgcTermsAcceptanceStore(preferences),
        remote = UgcTermsRemoteGateway { profileId, version ->
            iosUgcTermsRpcBoolean(configuration, session, transport, "quata_has_accepted_ugc_terms", profileId, version)
        },
        acceptance = UgcTermsAcceptanceGateway { profileId, version ->
            iosUgcTermsRpcUnit(configuration, session, transport, "quata_accept_ugc_terms", profileId, version)
        },
        pendingSyncLauncher = { task ->
            pendingSyncScope.launch {
                task()
            }
        },
    )
}

@OptIn(ExperimentalComposeUiApi::class)
fun QuataUgcTermsDialogViewController(
    profileId: String,
    languageCode: String,
    gateway: UgcTermsGateway,
    documentOpener: DocumentOpenService,
    onAccepted: () -> Unit,
    onLogout: () -> Unit,
): UIViewController = ComposeUIViewController(configure = { opaque = false }) {
    val language = languageCode.toUgcTermsLanguage()
    val scope = rememberCoroutineScope()
    var documentViewerState by remember { mutableStateOf<DocumentViewerState?>(null) }
    QuataTheme {
        QuataUgcTermsGateContent(
            profileId = profileId,
            gateway = gateway,
            strings = quataUgcTermsStrings(language),
            onAcceptedStateChanged = { accepted -> if (accepted == true) onAccepted() },
            onLogout = onLogout,
            legalLinks = {
                QuataLegalDocumentLinksColumnContent(
                    language = language,
                    documents = listOf(LegalDocument.ChildSafety, LegalDocument.Privacy),
                    onOpenDocument = { document ->
                        scope.launch {
                            documentViewerState = openIosUgcLegalDocumentWithViewerState(document, language, documentOpener)
                        }
                    },
                )
            },
        )
        QuataDocumentViewerStatusContent(
            state = documentViewerState,
            strings = quataDocumentViewerStatusStrings(language),
            onDismiss = { documentViewerState = null },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun QuataIosUgcTermsEvidenceViewController(
    languageCode: String?,
    onOpened: (String) -> Unit,
    onAccepted: () -> Unit,
    onLogout: () -> Unit,
): UIViewController = ComposeUIViewController(configure = { opaque = false }) {
    val language = (languageCode ?: "es").toUgcTermsLanguage()
    val gateway = remember { IosUgcTermsEvidenceGateway() }
    var documentViewerState by remember { mutableStateOf<DocumentViewerState?>(null) }
    QuataTheme {
        QuataUgcTermsGateContent(
            profileId = "ios-ugc-terms-fixture",
            gateway = gateway,
            strings = quataUgcTermsStrings(language),
            onAcceptedStateChanged = { accepted -> if (accepted == true) onAccepted() },
            onLogout = onLogout,
            legalLinks = {
                QuataLegalDocumentLinksColumnContent(
                    language = language,
                    documents = listOf(LegalDocument.ChildSafety, LegalDocument.Privacy),
                    onOpenDocument = { document ->
                        val file = iosLegalDocumentFile(document, language)
                            ?: iosLegalDocumentPlaceholderFile(document, language)
                        onOpened(file.displayName.orEmpty())
                        documentViewerState = DocumentViewerState.Opened(
                            file = file,
                            descriptor = documentViewerOpeningState(file).descriptor,
                        )
                    },
                )
            },
        )
        QuataDocumentViewerStatusContent(
            state = documentViewerState,
            strings = quataDocumentViewerStatusStrings(language),
            onDismiss = { documentViewerState = null },
        )
    }
}

private suspend fun iosUgcTermsRpcBoolean(
    configuration: IosAuthRuntimeConfiguration,
    session: IosRenewableAuthSession,
    transport: IosAuthHttpTransport,
    functionName: String,
    profileId: String,
    version: String,
): Result<Boolean> = iosUgcTermsRpc(configuration, session, transport, functionName, profileId, version).mapCatching { body ->
    Json.parseToJsonElement(body).jsonPrimitive.booleanOrNull ?: false
}

private suspend fun iosUgcTermsRpcUnit(
    configuration: IosAuthRuntimeConfiguration,
    session: IosRenewableAuthSession,
    transport: IosAuthHttpTransport,
    functionName: String,
    profileId: String,
    version: String,
): Result<Unit> = iosUgcTermsRpc(configuration, session, transport, functionName, profileId, version).map { Unit }

private suspend fun iosUgcTermsRpc(
    configuration: IosAuthRuntimeConfiguration,
    session: IosRenewableAuthSession,
    transport: IosAuthHttpTransport,
    functionName: String,
    profileId: String,
    version: String,
): Result<String> = runCatching {
    val active = session.currentSession() ?: error("ios_ugc_terms_session_required")
    val response = transport.post(
        endpoint = "${configuration.supabaseUrl.trim().trimEnd('/')}/rest/v1/rpc/$functionName",
        headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "apikey" to configuration.supabasePublishableKey.trim(),
            "Authorization" to "Bearer ${active.accessToken.orEmpty()}",
        ),
        body = buildJsonObject {
            put("p_actor_profile_id", profileId)
            put("p_terms_version", version)
        }.toString(),
    )
    check(response.statusCode in 200..299) { "ios_ugc_terms_http_${response.statusCode}" }
    response.body
}

private suspend fun openIosUgcLegalDocumentWithViewerState(
    document: LegalDocument,
    language: QuataLanguage,
    documentOpener: DocumentOpenService,
): DocumentViewerState {
    val file = iosLegalDocumentFile(document, language)
    if (file == null) {
        val placeholder = iosLegalDocumentPlaceholderFile(document, language)
        return DocumentViewerState.Failed(
            file = placeholder,
            descriptor = documentViewerOpeningState(placeholder).descriptor,
            reason = DocumentViewerFailureReason.PlatformUnsupported,
        )
    }
    return documentOpener.openWithViewerState(file).completed
}

private fun String.toUgcTermsLanguage(): QuataLanguage = when {
    startsWith("fr", ignoreCase = true) -> QuataLanguage.French
    startsWith("en", ignoreCase = true) -> QuataLanguage.English
    else -> QuataLanguage.Spanish
}

private class IosUgcTermsEvidenceGateway : UgcTermsGateway {
    private var accepted = false

    override suspend fun hasAcceptedTerms(version: String): Result<Boolean> =
        Result.success(accepted)

    override suspend fun acceptTerms(version: String): Result<Unit> {
        accepted = true
        return Result.success(Unit)
    }
}
