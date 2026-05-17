package com.qiuhu.embyflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = darkColorScheme(
    primary = AccentGreen,
    onPrimary = ScreenBase,
    secondary = AccentOrange,
    background = ScreenBase,
    onBackground = TextPrimary,
    surface = ScreenBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSelected,
    outline = OutlineSoft,
    scrim = ScreenBase,
)

@Composable
fun EmbyFlowTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        content = content,
    )
}
