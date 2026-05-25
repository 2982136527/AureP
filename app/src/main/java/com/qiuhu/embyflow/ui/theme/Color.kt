package com.qiuhu.embyflow.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

val BackgroundTop = Color(0xFFD8DFE7)
val BackgroundMid = Color(0xFFD2D9E2)
val BackgroundBottom = Color(0xFFCAD2DC)
val ScreenBase = Color(0xFFD3DAE3)
val SurfaceGlass = Color(0xFFD9E0E8)
val SurfaceGlassStrong = Color(0xFFDDE4EB)
val SurfaceSelected = Color(0xFFC7D0DA)
val SurfacePromo = Color(0xFFD0D8E1)
val AccentGreen = Color(0xFFB0AEC6)
val AccentOrange = Color(0xFFA3AFBC)
val TextPrimary = Color(0xFF263039)
val TextSecondary = Color(0xFF535C69)
val OutlineSoft = Color(0x22535C69)
val OverlayBackdrop = Color(0x26000000)
val GlassHighlight = Color(0xCCFFFFFF)
val GlassHighlightSoft = Color(0x96FFFFFF)
val GlassShadow = Color(0x7A8A9CAF)
val GlassTintTop = Color(0xFFDBE1E8)
val GlassTintBottom = Color(0xFFCBD3DD)

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
