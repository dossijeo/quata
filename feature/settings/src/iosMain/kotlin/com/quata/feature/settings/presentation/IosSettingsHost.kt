package com.quata.feature.settings.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.moderation.iosLegalDocumentFile
import com.quata.core.moderation.iosLegalDocumentPlaceholderFile
import com.quata.core.platform.DocumentOpenService
import com.quata.core.platform.DocumentViewerFailureReason
import com.quata.core.platform.DocumentViewerState
import com.quata.core.platform.documentViewerOpeningState
import com.quata.core.platform.openWithViewerState
import com.quata.core.ui.components.QuataDocumentViewerStatusContent
import com.quata.core.ui.components.quataDocumentViewerStatusStrings
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/** Swift supplies persisted appearance state and all navigation/platform actions. */
class IosSettingsHostDependencies(
    val touchFlowEnabled: Boolean,
    val themeMode: QuataThemeMode,
    val strings: AppearanceSettingsStrings,
    val language: QuataLanguage,
    val documentOpener: DocumentOpenService?,
    val openLegalDocument: (LegalDocument, DocumentOpenService) -> Unit,
    val onTouchFlowEnabledChange: (Boolean) -> Unit,
    val onThemeModeChange: (QuataThemeMode) -> Unit,
    val onLogout: () -> Unit,
)

/**
 * Swift-facing factory which keeps Kotlin enum construction and localized labels at the shared
 * boundary. Persistence stays with the UIKit launcher through the supplied callbacks.
 */
fun createIosSettingsHostDependencies(
    touchFlowEnabled: Boolean,
    themeModeStorageValue: String?,
    languageCode: String,
    documentOpener: DocumentOpenService?,
    openLegalDocument: (LegalDocument, DocumentOpenService) -> Unit,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeStorageValueChange: (String) -> Unit,
    onLogout: () -> Unit,
): IosSettingsHostDependencies = IosSettingsHostDependencies(
    touchFlowEnabled = touchFlowEnabled,
    themeMode = QuataThemeMode.fromStorageValue(themeModeStorageValue),
    strings = AppearanceSettingsStrings(
        touchFlow = "Touch Flow",
        theme = "Theme",
        system = "System",
        dark = "Dark",
        light = "Light",
    ),
    language = languageCode.toSettingsLanguage(),
    documentOpener = documentOpener,
    openLegalDocument = openLegalDocument,
    onTouchFlowEnabledChange = onTouchFlowEnabledChange,
    onThemeModeChange = { mode -> onThemeModeStorageValueChange(mode.storageValue) },
    onLogout = onLogout,
)

fun QuataSettingsViewController(dependencies: IosSettingsHostDependencies): UIViewController = ComposeUIViewController {
    var touchFlowEnabled by remember { mutableStateOf(dependencies.touchFlowEnabled) }
    var themeMode by remember { mutableStateOf(dependencies.themeMode) }
    var documentViewerState by remember { mutableStateOf<DocumentViewerState?>(null) }
    val scope = rememberCoroutineScope()
    QuataTheme(mode = themeMode) {
        SettingsScreenHost(
            touchFlowEnabled = touchFlowEnabled,
            themeMode = themeMode,
            strings = SettingsScreenStrings(
                appearance = dependencies.strings,
                legalDocuments = settingsLegalDocumentsStrings(dependencies.language),
                logout = "Log out",
            ),
            language = dependencies.language,
            onTouchFlowEnabledChange = { enabled ->
                touchFlowEnabled = enabled
                dependencies.onTouchFlowEnabledChange(enabled)
            },
            onThemeModeChange = { mode ->
                themeMode = mode
                dependencies.onThemeModeChange(mode)
            },
            onOpenDocument = { document ->
                dependencies.documentOpener?.let { opener ->
                    scope.launch {
                        val file = iosLegalDocumentFile(document, dependencies.language)
                        if (file == null) {
                            val placeholder = iosLegalDocumentPlaceholderFile(document, dependencies.language)
                            documentViewerState = DocumentViewerState.Failed(
                                file = placeholder,
                                descriptor = documentViewerOpeningState(placeholder).descriptor,
                                reason = DocumentViewerFailureReason.PlatformUnsupported,
                            )
                        } else {
                            documentViewerState = documentViewerOpeningState(file)
                            documentViewerState = opener.openWithViewerState(file).completed
                        }
                    }
                }
            },
            onLogout = dependencies.onLogout,
        )
        QuataDocumentViewerStatusContent(
            state = documentViewerState,
            strings = quataDocumentViewerStatusStrings(dependencies.language),
            onDismiss = { documentViewerState = null },
        )
    }
}

private fun String.toSettingsLanguage(): QuataLanguage = when (substringBefore('-').substringBefore('_').lowercase()) {
    "es" -> QuataLanguage.Spanish
    "fr" -> QuataLanguage.French
    else -> QuataLanguage.English
}
