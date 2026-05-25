package com.qiuhu.embyflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AppColorScheme = lightColorScheme(
    primary = AccentGreen,
    onPrimary = TextPrimary,
    secondary = AccentOrange,
    background = ScreenBase,
    onBackground = TextPrimary,
    surface = SurfaceGlass,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceSelected,
    outline = OutlineSoft,
    scrim = OverlayBackdrop,
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
