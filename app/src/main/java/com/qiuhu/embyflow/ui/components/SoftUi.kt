package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.ui.theme.AccentGreen
import com.qiuhu.embyflow.ui.theme.AccentOrange
import com.qiuhu.embyflow.ui.theme.GlassHighlight
import com.qiuhu.embyflow.ui.theme.GlassShadow
import com.qiuhu.embyflow.ui.theme.ScreenBase
import com.qiuhu.embyflow.ui.theme.SurfaceGlass
import com.qiuhu.embyflow.ui.theme.SurfaceGlassStrong
import com.qiuhu.embyflow.ui.theme.SurfaceSelected
import com.qiuhu.embyflow.ui.theme.TextPrimary
import com.qiuhu.embyflow.ui.theme.TextSecondary

val SoftUiBackground = ScreenBase
val SoftUiSurface = SurfaceGlass
val SoftUiSurfaceRaised = SurfaceGlassStrong
val SoftUiSurfacePressed = SurfaceSelected
val SoftUiTextPrimary = TextPrimary
val SoftUiTextSecondary = TextSecondary
val SoftUiAccent = AccentGreen
val SoftUiAccentSecondary = AccentOrange
val SoftUiShadowDark = GlassShadow
val SoftUiShadowLight = GlassHighlight
val SoftUiScrim = Color(0x26000000)

enum class SoftUiSurfaceStyle {
    Raised,
    Inset,
}

fun Modifier.softUiRaisedSurface(
    shape: Shape,
    color: Color = SoftUiSurface,
    shadowRadius: Dp = 8.dp,
    shadowOffset: Dp = 2.dp,
): Modifier = composed {
    this
        .softUiOuterShadow(
            shape = shape,
            lightShadowColor = SoftUiShadowLight,
            darkShadowColor = SoftUiShadowDark,
            blurRadius = shadowRadius,
            offset = shadowOffset,
        )
        .clip(shape)
        .background(color)
        .softUiRaisedBevel(shape = shape)
}

fun Modifier.softUiInsetSurface(
    shape: Shape,
    color: Color = SoftUiSurfacePressed,
    shadowRadius: Dp = 4.dp,
    shadowOffset: Dp = 1.dp,
): Modifier = composed {
    this
        .clip(shape)
        .background(color)
        .softUiInsetBevel(
            shape = shape,
            shadowRadius = shadowRadius,
            shadowOffset = shadowOffset,
        )
}

fun Modifier.softUiSurface(
    shape: Shape,
    style: SoftUiSurfaceStyle,
    color: Color = if (style == SoftUiSurfaceStyle.Raised) SoftUiSurface else SoftUiSurfacePressed,
): Modifier = when (style) {
    SoftUiSurfaceStyle.Raised -> softUiRaisedSurface(shape = shape, color = color)
    SoftUiSurfaceStyle.Inset -> softUiInsetSurface(shape = shape, color = color)
}

fun softUiPressedColor(base: Color): Color = lerp(base, Color.Black, 0.045f)

private fun Modifier.softUiOuterShadow(
    shape: Shape,
    lightShadowColor: Color,
    darkShadowColor: Color,
    blurRadius: Dp,
    offset: Dp,
): Modifier = shadow(
    elevation = (blurRadius.value * 0.55f).dp,
    shape = shape,
    clip = false,
    ambientColor = darkShadowColor.copy(alpha = 0.14f),
    spotColor = darkShadowColor.copy(alpha = 0.18f),
)

private fun Modifier.softUiRaisedBevel(
    shape: Shape,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = outline.toPath()
    val lightStrokeWidth = 0.9.dp.toPx()
    val darkStrokeWidth = 1.2.dp.toPx()
    val lightBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to SoftUiShadowLight.copy(alpha = 0.34f),
            0.42f to SoftUiShadowLight.copy(alpha = 0.08f),
            1f to Color.Transparent,
        ),
        start = Offset.Zero,
        end = Offset(size.width * 0.72f, size.height * 0.72f),
    )
    val darkBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.56f to SoftUiShadowDark.copy(alpha = 0.04f),
            1f to SoftUiShadowDark.copy(alpha = 0.10f),
        ),
        start = Offset(size.width * 0.18f, size.height * 0.18f),
        end = Offset(size.width, size.height),
    )

    onDrawWithContent {
        drawContent()
        drawPath(
            path = path,
            brush = darkBrush,
            style = Stroke(width = darkStrokeWidth),
        )
        drawPath(
            path = path,
            brush = lightBrush,
            style = Stroke(width = lightStrokeWidth),
        )
    }
}

private fun Modifier.softUiInsetBevel(
    shape: Shape,
    shadowRadius: Dp,
    shadowOffset: Dp,
): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = outline.toPath()
    val lightStrokeWidth = shadowRadius.toPx() * 0.18f
    val darkStrokeWidth = shadowRadius.toPx() * 0.22f
    val travel = shadowOffset.toPx().coerceAtLeast(1f)
    val darkBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to SoftUiShadowDark.copy(alpha = 0.12f),
            0.42f to SoftUiShadowDark.copy(alpha = 0.06f),
            1f to Color.Transparent,
        ),
        start = Offset.Zero,
        end = Offset(size.width * 0.66f + travel, size.height * 0.66f + travel),
    )
    val lightBrush = Brush.linearGradient(
        colorStops = arrayOf(
            0f to Color.Transparent,
            0.58f to SoftUiShadowLight.copy(alpha = 0.05f),
            1f to SoftUiShadowLight.copy(alpha = 0.20f),
        ),
        start = Offset(size.width * 0.22f, size.height * 0.22f),
        end = Offset(size.width, size.height),
    )

    onDrawWithContent {
        drawContent()
        drawPath(
            path = path,
            brush = darkBrush,
            style = Stroke(width = darkStrokeWidth),
        )
        drawPath(
            path = path,
            brush = lightBrush,
            style = Stroke(width = lightStrokeWidth),
        )
    }
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply {
        addRoundRect(
            RoundRect(
                left = roundRect.left,
                top = roundRect.top,
                right = roundRect.right,
                bottom = roundRect.bottom,
                topLeftCornerRadius = roundRect.topLeftCornerRadius,
                topRightCornerRadius = roundRect.topRightCornerRadius,
                bottomRightCornerRadius = roundRect.bottomRightCornerRadius,
                bottomLeftCornerRadius = roundRect.bottomLeftCornerRadius,
            ),
        )
    }
}
