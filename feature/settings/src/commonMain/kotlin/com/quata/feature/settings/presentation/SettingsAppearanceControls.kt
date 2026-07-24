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
import com.quata.core.ui.components.QuataPanel

data class AppearanceSettingsStrings(val touchFlow: String, val theme: String, val system: String, val dark: String, val light: String)

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

@Composable
private fun ThemeModeOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val template = quataTheme()
    Surface(color = if (selected) template.colors.accent else template.colors.surfaceAlt, contentColor = if (selected) template.colors.accentContent else template.colors.textPrimary, shape = RoundedCornerShape(14.dp), modifier = modifier.height(40.dp).border(1.dp, if (selected) template.colors.accent else template.colors.divider, RoundedCornerShape(14.dp)).clickable(onClick = onClick)) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = template.textSizes.caption, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
