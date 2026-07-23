package com.quata.core.designsystem.theme

/** Compose Multiplatform theme and token definitions. */

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class QuataThemeMode(val storageValue: String) {
    System("system"),
    Dark("dark-mode"),
    Light("light-mode");

    companion object {
        fun fromStorageValue(value: String?): QuataThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

enum class QuataResolvedTheme(val templateId: String) {
    Dark("dark-mode"),
    Light("light-mode")
}

@Immutable
data class QuataChatBackgroundPalette(
    val base: Color,
    val a: Color,
    val b: Color,
    val c: Color
)

@Immutable
data class QuataThemeColors(
    val background: Color,
    val topChrome: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val surfaceRaised: Color,
    val selectedSurface: Color,
    val selectedBorder: Color,
    val accent: Color,
    val accentSoft: Color,
    val accentContent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val inputBorder: Color,
    val scrim: Color,
    val mediaScrim: Color,
    val success: Color,
    val warning: Color,
    val live: Color,
    val favorite: Color,
    val error: Color,
    val sos: Color,
    val sosSurface: Color,
    val chatMine: Color,
    val chatOther: Color,
    val chatSelected: Color,
    val chatBackgroundPalettes: List<QuataChatBackgroundPalette>
)

@Immutable
data class QuataThemeTemplate(
    val id: String,
    val resolvedTheme: QuataResolvedTheme,
    val colors: QuataThemeColors,
    val textSizes: QuataTextSizes
)

object QuataTemplates {
    val darkMode = QuataThemeTemplate(
        id = "dark-mode",
        resolvedTheme = QuataResolvedTheme.Dark,
        colors = QuataThemeColors(
            background = Color(0xFF0B1220),
            topChrome = Color(0xFF0B1220),
            surface = Color(0xFF171A23),
            surfaceAlt = Color(0xFF202432),
            surfaceRaised = Color(0xFF111827),
            selectedSurface = Color(0xFF0F315D),
            selectedBorder = Color(0xFF2F80ED),
            accent = Color(0xFFFF7A1A),
            accentSoft = Color(0xFFFFA451),
            accentContent = Color.Black,
            textPrimary = Color(0xFFF8F4EF),
            textSecondary = Color(0xFFB8B1AA),
            divider = Color(0xFF303545),
            inputBorder = Color(0xFF918C9A),
            scrim = Color.Black.copy(alpha = 0.63f),
            mediaScrim = Color.Black.copy(alpha = 0.42f),
            success = Color(0xFF18C6A3),
            warning = Color(0xFFFFC55C),
            live = Color(0xFFE5D45C),
            favorite = Color(0xFFFF5A8E),
            error = Color(0xFFFF5C5C),
            sos = Color(0xFFE0303B),
            sosSurface = Color(0xFF5B2730),
            chatMine = Color(0xFFFFB45F),
            chatOther = Color(0xFF171A23),
            chatSelected = Color(0xFFFFF3C4),
            chatBackgroundPalettes = listOf(
                chatPalette("#030408", "#2f8cff", "#7c3cff", "#ff8a1f"),
                chatPalette("#020508", "#00b3ff", "#18c6a3", "#ffb12b"),
                chatPalette("#040308", "#815bff", "#b84cff", "#ff4c1a"),
                chatPalette("#030507", "#3a86ff", "#ff006e", "#ffbe0b"),
                chatPalette("#020609", "#00b3ff", "#1aa7ff", "#815bff"),
                chatPalette("#040506", "#ffb238", "#815bff", "#1aa7ff"),
                chatPalette("#020406", "#18c6a3", "#2f8cff", "#7c3cff"),
                chatPalette("#040306", "#b84cff", "#ff315f", "#ff8a1f")
            )
        ),
        textSizes = QuataDefaultTextSizes
    )

    val lightMode = QuataThemeTemplate(
        id = "light-mode",
        resolvedTheme = QuataResolvedTheme.Light,
        colors = QuataThemeColors(
            background = Color(0xFFFFF4EA),
            topChrome = Color(0xFFFFF4EA),
            surface = Color(0xFFFFFFFF),
            surfaceAlt = Color(0xFFF5F0EA),
            surfaceRaised = Color(0xFFFFFEFC),
            selectedSurface = Color(0xFFFFE4CE),
            selectedBorder = Color(0xFFFF7A1A),
            accent = Color(0xFFFF7A1A),
            accentSoft = Color(0xFFFFA451),
            accentContent = Color.Black,
            textPrimary = Color(0xFF181512),
            textSecondary = Color(0xFF756B62),
            divider = Color(0xFFE6DDD3),
            inputBorder = Color(0xFF79747E),
            scrim = Color.Transparent,
            mediaScrim = Color.Black.copy(alpha = 0.30f),
            success = Color(0xFF0F8F76),
            warning = Color(0xFFC97800),
            live = Color(0xFF8A6F00),
            favorite = Color(0xFFE03B73),
            error = Color(0xFFD82936),
            sos = Color(0xFFE0303B),
            sosSurface = Color(0xFFFFE0E0),
            chatMine = Color(0xFFFFD2A3),
            chatOther = Color(0xFFFFFBF7),
            chatSelected = Color(0xFFFFEAB3),
            chatBackgroundPalettes = listOf(
                chatPalette("#efe1d3", "#e9781e", "#5f9bd6", "#c95f12"),
                chatPalette("#e8f1fb", "#438fd0", "#e59a55", "#cf6816"),
                chatPalette("#f3e4d4", "#d97624", "#5caaa4", "#bf5d13"),
                chatPalette("#ece5f6", "#9b78d6", "#d98a35", "#cc6819"),
                chatPalette("#e9f3ee", "#42a698", "#d17f3d", "#c95f12"),
                chatPalette("#f0e5d6", "#d86620", "#669ecf", "#d7891d")
            )
        ),
        textSizes = QuataDefaultTextSizes
    )

    fun templateFor(resolvedTheme: QuataResolvedTheme): QuataThemeTemplate =
        when (resolvedTheme) {
            QuataResolvedTheme.Dark -> darkMode
            QuataResolvedTheme.Light -> lightMode
        }
}

private val LocalQuataTemplate = staticCompositionLocalOf { QuataTemplates.darkMode }

object QuataThemeTokens {
    val current: QuataThemeTemplate
        @Composable get() = LocalQuataTemplate.current
}

@Composable
fun quataTheme(): QuataThemeTemplate = QuataThemeTokens.current

private val QuataDarkColors = darkColorScheme(
    primary = QuataTemplates.darkMode.colors.accent,
    onPrimary = QuataTemplates.darkMode.colors.accentContent,
    secondary = QuataTemplates.darkMode.colors.accentSoft,
    background = QuataTemplates.darkMode.colors.background,
    onBackground = QuataTemplates.darkMode.colors.textPrimary,
    surface = QuataTemplates.darkMode.colors.surface,
    onSurface = QuataTemplates.darkMode.colors.textPrimary,
    surfaceVariant = QuataTemplates.darkMode.colors.surfaceAlt,
    onSurfaceVariant = QuataTemplates.darkMode.colors.textSecondary,
    error = QuataTemplates.darkMode.colors.error
)

private val QuataLightColors = lightColorScheme(
    primary = QuataTemplates.lightMode.colors.accent,
    onPrimary = QuataTemplates.lightMode.colors.accentContent,
    secondary = QuataTemplates.lightMode.colors.accentSoft,
    background = QuataTemplates.lightMode.colors.background,
    onBackground = QuataTemplates.lightMode.colors.textPrimary,
    surface = QuataTemplates.lightMode.colors.surface,
    onSurface = QuataTemplates.lightMode.colors.textPrimary,
    surfaceVariant = QuataTemplates.lightMode.colors.surfaceAlt,
    onSurfaceVariant = QuataTemplates.lightMode.colors.textSecondary,
    error = QuataTemplates.lightMode.colors.error
)

@Composable
fun QuataTheme(
    mode: QuataThemeMode = QuataThemeMode.System,
    content: @Composable () -> Unit
) {
    val systemIsDark = isSystemInDarkTheme()
    val resolvedTheme = when (mode) {
        QuataThemeMode.System -> if (systemIsDark) QuataResolvedTheme.Dark else QuataResolvedTheme.Light
        QuataThemeMode.Dark -> QuataResolvedTheme.Dark
        QuataThemeMode.Light -> QuataResolvedTheme.Light
    }
    val template = QuataTemplates.templateFor(resolvedTheme)
    val colorScheme = when (resolvedTheme) {
        QuataResolvedTheme.Dark -> QuataDarkColors
        QuataResolvedTheme.Light -> QuataLightColors
    }
    CompositionLocalProvider(LocalQuataTemplate provides template) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = QuataTypography,
            shapes = Shapes(),
            content = content
        )
    }
}

private fun chatPalette(base: String, a: String, b: String, c: String): QuataChatBackgroundPalette =
    QuataChatBackgroundPalette(hexColor(base), hexColor(a), hexColor(b), hexColor(c))

private fun hexColor(value: String): Color =
    Color(value.removePrefix("#").toLong(16) or 0xFF000000)
