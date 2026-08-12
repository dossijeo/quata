package com.quata.feature.settings.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.ComposeUIViewController
import com.quata.core.designsystem.theme.QuataTheme
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.platform.DocumentOpenService
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
)

fun QuataSettingsViewController(dependencies: IosSettingsHostDependencies): UIViewController = ComposeUIViewController {
    var touchFlowEnabled by remember { mutableStateOf(dependencies.touchFlowEnabled) }
    var themeMode by remember { mutableStateOf(dependencies.themeMode) }
    QuataTheme(mode = themeMode) {
        Column {
            AppearanceSettingsSectionContent(
                touchFlowEnabled = touchFlowEnabled,
                themeMode = themeMode,
                strings = dependencies.strings,
                onTouchFlowEnabledChange = { enabled ->
                    touchFlowEnabled = enabled
                    dependencies.onTouchFlowEnabledChange(enabled)
                },
                onThemeModeChange = { mode ->
                    themeMode = mode
                    dependencies.onThemeModeChange(mode)
                },
            )
            dependencies.documentOpener?.let { opener ->
                SettingsLegalDocumentsSectionContent(
                    language = dependencies.language,
                    strings = SettingsLegalDocumentsStrings(title = "Legal documents"),
                    onOpenDocument = { document ->
                        dependencies.openLegalDocument(document, opener)
                    },
                )
            }
        }
    }
}

private fun String.toSettingsLanguage(): QuataLanguage = when (substringBefore('-').substringBefore('_').lowercase()) {
    "es" -> QuataLanguage.Spanish
    "fr" -> QuataLanguage.French
    else -> QuataLanguage.English
}
