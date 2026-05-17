package com.qiuhu.embyflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

val BackgroundTop = Color(0xFF09111D)
val BackgroundMid = Color(0xFF151E34)
val BackgroundBottom = Color(0xFF33263B)
val ScreenBase = Color(0xFF050913)
val SurfaceGlass = Color(0x18F9FBFF)
val SurfaceGlassStrong = Color(0x28FFFFFF)
val SurfaceSelected = Color(0x40EAF5FF)
val SurfacePromo = Color(0x2CDCE9FF)
val AccentGreen = Color(0xFFA4FF77)
val AccentOrange = Color(0xFFFFB85C)
val TextPrimary = Color(0xFFF6FAFF)
val TextSecondary = Color(0xFFC2CCDE)
val OutlineSoft = Color(0x4AFFFFFF)
val OverlayBackdrop = Color(0xDD050913)
val GlassHighlight = Color(0xE0FFFFFF)
val GlassHighlightSoft = Color(0x58FFFFFF)
val GlassShadow = Color(0x5A061021)
val GlassTintTop = Color(0x34E4F1FF)
val GlassTintBottom = Color(0x1AFFF0E8)

val ColorScheme.backgroundGradient: List<Color>
    get() = listOf(ScreenBase, BackgroundTop, BackgroundMid, BackgroundBottom)

val ColorScheme.surfaceGlass: Color
    get() = SurfaceGlass

val ColorScheme.surfaceGlassStrong: Color
    get() = SurfaceGlassStrong

val ColorScheme.surfaceSelected: Color
    get() = SurfaceSelected

val ColorScheme.surfacePromo: Color
    get() = SurfacePromo

val ColorScheme.onSurfaceMuted: Color
    get() = TextSecondary

val ColorScheme.overlayBackdrop: Color
    get() = OverlayBackdrop

val ColorScheme.glassHighlight: Color
    get() = GlassHighlight

val ColorScheme.glassHighlightSoft: Color
    get() = GlassHighlightSoft

val ColorScheme.glassShadow: Color
    get() = GlassShadow

val ColorScheme.glassTintTop: Color
    get() = GlassTintTop

val ColorScheme.glassTintBottom: Color
    get() = GlassTintBottom
