package com.ricebaby.video.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 固定品牌配色（不用系统动态取色：随机色相是"脏"的根源）
private val LightColors = lightColorScheme(
    primary = Teal40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = Teal0,
    secondary = Slate40,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF06201C),
    tertiary = Gold40,
    onTertiary = androidx.compose.ui.graphics.Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF2A1C00),
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = androidx.compose.ui.graphics.Color(0xFF757974),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFC5C9C2),
    error = androidx.compose.ui.graphics.Color(0xFFB3261E),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFF9DEDC),
)

private val DarkColors = darkColorScheme(
    primary = Teal80,
    onPrimary = Teal0,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF005048),
    onPrimaryContainer = TealContainer,
    secondary = Slate80,
    onSecondary = androidx.compose.ui.graphics.Color(0xFF122B26),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF334B46),
    onSecondaryContainer = SlateContainer,
    tertiary = Gold80,
    onTertiary = androidx.compose.ui.graphics.Color(0xFF332600),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF6C5207),
    onTertiaryContainer = GoldContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = androidx.compose.ui.graphics.Color(0xFF8B938C),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFF414842),
    error = androidx.compose.ui.graphics.Color(0xFFF2B8B5),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF8C1D18),
)

@Composable
fun RicebabyVideoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
