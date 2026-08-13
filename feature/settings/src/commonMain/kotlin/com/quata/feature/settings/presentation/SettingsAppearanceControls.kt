package com.quata.feature.settings.presentation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quata.core.designsystem.theme.QuataThemeMode
import com.quata.core.designsystem.theme.quataTheme
import com.quata.core.localization.QuataLanguage
import com.quata.core.moderation.LegalDocument
import com.quata.core.ui.components.QuataLegalDocumentLinksContent
import com.quata.core.ui.components.QuataPanel
import com.quata.core.ui.components.QuataDocumentViewerStatusStrings

data class AppearanceSettingsStrings(val touchFlow: String, val theme: String, val system: String, val dark: String, val light: String)
data class SettingsLegalDocumentsStrings(val title: String)

fun settingsLegalDocumentsStrings(language: QuataLanguage): SettingsLegalDocumentsStrings =
    SettingsLegalDocumentsStrings(
        title = when (language) {
            QuataLanguage.Spanish -> "Documentos legales"
            QuataLanguage.French -> "Documents juridiques"
            QuataLanguage.English -> "Legal documents"
        },
    )

fun settingsDocumentViewerStatusStrings(language: QuataLanguage): QuataDocumentViewerStatusStrings = when (language) {
    QuataLanguage.Spanish -> QuataDocumentViewerStatusStrings(
        openingTitle = "Abriendo documento",
        openedTitle = "Documento abierto",
        failedTitle = "No se pudo abrir",
        openingMessage = "Preparando el visor de documentos.",
        openedMessage = "El documento se abrio en el visor del sistema.",
        cancelledMessage = "La apertura se cancelo antes de mostrar el documento.",
        unsupportedFormatMessage = "Este formato no se puede previsualizar.",
        platformUnsupportedMessage = "Este dispositivo no puede abrir documentos desde aqui.",
        openFailedMessage = "No se pudo abrir el documento.",
        closeLabel = "Cerrar",
    )
    QuataLanguage.French -> QuataDocumentViewerStatusStrings(
        openingTitle = "Ouverture du document",
        openedTitle = "Document ouvert",
        failedTitle = "Impossible d'ouvrir",
        openingMessage = "Preparation de l'apercu du document.",
        openedMessage = "Le document s'est ouvert dans le lecteur du systeme.",
        cancelledMessage = "L'ouverture a ete annulee avant l'affichage.",
        unsupportedFormatMessage = "Ce format ne peut pas etre previsualise.",
        platformUnsupportedMessage = "Cet appareil ne peut pas ouvrir les documents ici.",
        openFailedMessage = "Impossible d'ouvrir le document.",
        closeLabel = "Fermer",
    )
    QuataLanguage.English -> QuataDocumentViewerStatusStrings(
        openingTitle = "Opening document",
        openedTitle = "Document opened",
        failedTitle = "Could not open",
        openingMessage = "Preparing the document viewer.",
        openedMessage = "The document opened in the system viewer.",
        cancelledMessage = "Opening was cancelled before the document was shown.",
        unsupportedFormatMessage = "This format cannot be previewed.",
        platformUnsupportedMessage = "This device cannot open documents here.",
        openFailedMessage = "The document could not be opened.",
        closeLabel = "Close",
    )
}

/** Shared settings-card shell; the host supplies only localized strings and persisted values. */
@Composable
fun AppearanceSettingsSectionContent(
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    strings: AppearanceSettingsStrings,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataPanel(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        AppearanceSettingsControls(
            touchFlowEnabled = touchFlowEnabled,
            themeMode = themeMode,
            strings = strings,
            onTouchFlowEnabledChange = onTouchFlowEnabledChange,
            onThemeModeChange = onThemeModeChange,
        )
    }
}

@Composable
fun AppearanceSettingsControls(
    touchFlowEnabled: Boolean,
    themeMode: QuataThemeMode,
    strings: AppearanceSettingsStrings,
    onTouchFlowEnabledChange: (Boolean) -> Unit,
    onThemeModeChange: (QuataThemeMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.touchFlow, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Switch(touchFlowEnabled, onTouchFlowEnabledChange)
        }
        Text(strings.theme, fontWeight = FontWeight.ExtraBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeModeOption(strings.system, themeMode == QuataThemeMode.System, { onThemeModeChange(QuataThemeMode.System) }, Modifier.weight(1f))
            ThemeModeOption(strings.dark, themeMode == QuataThemeMode.Dark, { onThemeModeChange(QuataThemeMode.Dark) }, Modifier.weight(1f))
            ThemeModeOption(strings.light, themeMode == QuataThemeMode.Light, { onThemeModeChange(QuataThemeMode.Light) }, Modifier.weight(1f))
        }
    }
}

/** Shared legal-documents settings section; platform hosts only resolve the selected document. */
@Composable
fun SettingsLegalDocumentsSectionContent(
    language: QuataLanguage,
    strings: SettingsLegalDocumentsStrings,
    onOpenDocument: (LegalDocument) -> Unit,
    modifier: Modifier = Modifier,
) {
    QuataPanel(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.title, fontWeight = FontWeight.ExtraBold)
            QuataLegalDocumentLinksContent(
                language = language,
                onOpenDocument = onOpenDocument,
            )
        }
    }
}

@Composable
private fun ThemeModeOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val template = quataTheme()
    Surface(color = if (selected) template.colors.accent else template.colors.surfaceAlt, contentColor = if (selected) template.colors.accentContent else template.colors.textPrimary, shape = RoundedCornerShape(14.dp), modifier = modifier.height(40.dp).border(1.dp, if (selected) template.colors.accent else template.colors.divider, RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = template.textSizes.caption, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
