package com.qiuhu.embyflow.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import kotlin.math.cos
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.ui.theme.backgroundGradient
import com.qiuhu.embyflow.ui.theme.glassHighlight
import com.qiuhu.embyflow.ui.theme.glassHighlightSoft
import com.qiuhu.embyflow.ui.theme.glassShadow
import com.qiuhu.embyflow.ui.theme.glassTintBottom
import com.qiuhu.embyflow.ui.theme.glassTintTop
import com.qiuhu.embyflow.ui.theme.surfaceGlass
import com.qiuhu.embyflow.ui.theme.surfaceGlassStrong
import com.qiuhu.embyflow.ui.theme.surfaceSelected

private const val LIQUID_GLASS_SHADER = """
uniform shader composable;
uniform float2 resolution;
uniform float time;
uniform float distortion;
uniform float glow;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float2 centered = uv - 0.5;

    float waveA = sin((uv.y * 22.0) + time * 0.9) * 0.006;
    float waveB = cos((uv.x * 19.0) - time * 1.05) * 0.005;
    float ripple = sin((uv.x + uv.y) * 26.0 + time * 1.28) * 0.0035;
    float swirl = sin(length(centered) * 30.0 - time * 1.45) * 0.0025;

    float2 offset = float2(waveA + ripple, waveB + swirl) * distortion;
    half4 base = composable.eval(fragCoord + offset * resolution);

    float edge = 1.0 - smoothstep(0.10, 0.44, abs(centered.x) + abs(centered.y) * 0.82);
    float sweep = smoothstep(-0.18, 0.10, centered.x + centered.y * 0.55 + sin(time * 0.7) * 0.24);
    float caustic = sin((uv.x * 36.0) - (uv.y * 18.0) + time * 1.55) * 0.5 + 0.5;
    float fresnel = pow(1.0 - max(0.0, 1.0 - length(centered) * 1.86), 2.3);

    base.rgb += edge * 0.045 * glow;
    base.rgb += sweep * 0.040 * glow;
    base.rgb += caustic * 0.028 * edge * glow;
    base.rgb += fresnel * 0.024 * glow;
    return base;
}
"""

@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
) {
    val backgroundColors = MaterialTheme.colorScheme.backgroundGradient
    val motion = rememberInfiniteTransition(label = "glass-backdrop")
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glass-backdrop-drift",
    )

    Box(
        modifier = modifier.drawWithCache {
            val topGlow = Brush.radialGradient(
                colors = listOf(Color(0x663D86FF), Color.Transparent),
                center = Offset(size.width * (0.16f + 0.06f * drift), size.height * 0.18f),
                radius = size.minDimension * 0.95f,
            )
            val sideGlow = Brush.radialGradient(
                colors = listOf(Color(0x4236F0D2), Color.Transparent),
                center = Offset(size.width * 0.92f, size.height * (0.26f + 0.12f * drift)),
                radius = size.minDimension * 0.75f,
            )
            val bottomGlow = Brush.radialGradient(
                colors = listOf(Color(0x46FF8A64), Color.Transparent),
                center = Offset(size.width * (0.42f + 0.14f * drift), size.height * 1.02f),
                radius = size.minDimension * 0.9f,
            )
            val causticA = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.05f),
                    Color.Transparent,
                ),
                start = Offset(-size.width * 0.15f, size.height * (0.12f + 0.16f * drift)),
                end = Offset(size.width * 0.85f, size.height * (0.44f + 0.18f * drift)),
            )
            val causticB = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x88D6F6FF).copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                start = Offset(size.width * 0.2f, size.height * (0.72f - 0.16f * drift)),
                end = Offset(size.width * 1.12f, size.height * (0.38f - 0.08f * drift)),
            )

            onDrawBehind {
                drawRect(Brush.verticalGradient(backgroundColors))
                drawRect(topGlow)
                drawRect(sideGlow)
                drawRect(bottomGlow)
                drawRect(causticA, blendMode = BlendMode.Screen)
                drawRect(causticB, blendMode = BlendMode.Screen)
            }
        },
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    emphasis: GlassEmphasis = GlassEmphasis.Regular,
    backgroundLayer: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = rememberInfiniteTransition(label = "liquid-glass-panel")
    val sweep by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glass-sweep",
    )
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 11800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glass-drift",
    )
    val pulse by motion.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glass-pulse",
    )
    val shaderTime by motion.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glass-shader-time",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.982f else 1f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "glass-scale",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (pressed) 3f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "glass-offset",
    )

    val topTint = MaterialTheme.colorScheme.glassTintTop
    val baseTint = MaterialTheme.colorScheme.surfaceGlass
    val strongTint = MaterialTheme.colorScheme.surfaceGlassStrong
    val selectedTint = MaterialTheme.colorScheme.surfaceSelected
    val bottomTint = MaterialTheme.colorScheme.glassTintBottom
    val rimSoft = MaterialTheme.colorScheme.glassHighlightSoft
    val shadowColor = MaterialTheme.colorScheme.glassShadow
    val liquidBoost = when (emphasis) {
        GlassEmphasis.Regular -> 0.85f
        GlassEmphasis.Strong -> 1f
        GlassEmphasis.Selected -> 1.18f
    }
    val fillAlpha = when (emphasis) {
        GlassEmphasis.Regular -> 0.22f
        GlassEmphasis.Strong -> 0.28f
        GlassEmphasis.Selected -> 0.34f
    }
    val smokeBrush = when (emphasis) {
        GlassEmphasis.Regular -> Brush.verticalGradient(
            listOf(
                Color(0x22040A15),
                Color(0x16081218),
                Color(0x28050A16),
            ),
        )

        GlassEmphasis.Strong -> Brush.verticalGradient(
            listOf(
                Color(0x24040B17),
                Color(0x1809141C),
                Color(0x2A050A18),
            ),
        )

        GlassEmphasis.Selected -> Brush.verticalGradient(
            listOf(
                Color(0x20050C18),
                Color(0x180B1522),
                Color(0x26060B1A),
            ),
        )
    }

    val fillBrush = when (emphasis) {
        GlassEmphasis.Regular -> Brush.verticalGradient(
            listOf(
                topTint.scaleAlpha(0.54f),
                baseTint.scaleAlpha(0.42f),
                bottomTint.scaleAlpha(0.46f),
            ),
        )

        GlassEmphasis.Strong -> Brush.verticalGradient(
            listOf(
                rimSoft.scaleAlpha(0.32f),
                strongTint.scaleAlpha(0.52f),
                bottomTint.scaleAlpha(0.54f),
            ),
        )

        GlassEmphasis.Selected -> Brush.verticalGradient(
            listOf(
                rimSoft.scaleAlpha(0.40f),
                selectedTint.scaleAlpha(0.58f),
                bottomTint.scaleAlpha(0.62f),
            ),
        )
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (emphasis == GlassEmphasis.Selected) 30.dp else if (emphasis == GlassEmphasis.Strong) 26.dp else 18.dp,
                shape = shape,
                clip = false,
                ambientColor = shadowColor.copy(alpha = 0.46f),
                spotColor = shadowColor.copy(alpha = 0.74f),
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offsetY
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = outline.asPath()

                val liquidSweep = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.10f * liquidBoost),
                        Color.Transparent,
                    ),
                    start = Offset(size.width * (sweep - 0.34f), -size.height * 0.12f),
                    end = Offset(size.width * (sweep + 0.22f), size.height * 1.06f),
                )
                val causticBand = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFFD8F6FF).copy(alpha = 0.08f * liquidBoost),
                        Color(0xFFFFDEC8).copy(alpha = 0.05f * liquidBoost),
                        Color.Transparent,
                    ),
                    start = Offset(-size.width * 0.18f, size.height * (0.16f + 0.10f * drift)),
                    end = Offset(size.width * 0.90f, size.height * (0.58f + 0.08f * drift)),
                )
                val topLens = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f * liquidBoost),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * (0.18f + 0.10f * drift), size.height * 0.12f),
                    radius = size.minDimension * 0.72f * pulse,
                )
                val bottomLens = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFF2FDFF).copy(alpha = 0.07f * liquidBoost),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * (0.80f - 0.16f * drift), size.height * (0.80f - 0.08f * drift)),
                    radius = size.minDimension * 0.60f * (2.02f - pulse),
                )
                val topRim = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.17f * liquidBoost),
                        Color.White.copy(alpha = 0.04f * liquidBoost),
                        Color.Transparent,
                    ),
                )
                val leftRim = Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.07f * liquidBoost),
                        Color.Transparent,
                    ),
                )
                val meniscus = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.06f * liquidBoost),
                        Color.Transparent,
                    ),
                    start = Offset(-size.width * 0.14f, size.height * (0.30f - 0.10f * drift)),
                    end = Offset(size.width * 0.72f, size.height * (0.06f + 0.06f * drift)),
                )
                val innerShade = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color.Transparent,
                        shadowColor.copy(alpha = 0.18f),
                    ),
                )
                val innerGlow = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f * liquidBoost),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * (0.26f + 0.18f * sweep), size.height * 0.03f),
                    radius = size.minDimension * 0.46f,
                )
                onDrawWithContent draw@{
                    clipPath(path) {
                        drawRect(smokeBrush)
                        drawRect(fillBrush, alpha = fillAlpha)
                        drawRect(topLens, blendMode = BlendMode.Screen)
                        drawRect(bottomLens, blendMode = BlendMode.Screen)
                        drawRect(liquidSweep, blendMode = BlendMode.Screen)
                        drawRect(causticBand, blendMode = BlendMode.Screen)
                        drawRect(topRim, blendMode = BlendMode.Screen)
                        drawRect(leftRim, blendMode = BlendMode.Screen)
                        drawRect(meniscus, blendMode = BlendMode.Screen)
                        drawRect(innerGlow, blendMode = BlendMode.Screen)
                        drawRect(innerShade)
                        this@draw.drawContent()
                    }
                }
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        LiquidGlassShaderLayer(
            time = shaderTime,
            distortion = when (emphasis) {
                GlassEmphasis.Regular -> 0.9f
                GlassEmphasis.Strong -> 1f
                GlassEmphasis.Selected -> 1.12f
            },
            glow = liquidBoost,
            content = backgroundLayer,
        )
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun GlassCircleButton(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    emphasis: GlassEmphasis = GlassEmphasis.Strong,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassPanel(
        modifier = modifier,
        shape = CircleShape,
        onClick = onClick,
        emphasis = emphasis,
        content = content,
    )
}

enum class GlassEmphasis {
    Regular,
    Strong,
    Selected,
}

private fun Color.scaleAlpha(factor: Float): Color {
    return copy(alpha = (alpha * factor).coerceIn(0f, 1f))
}

private fun Outline.asPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
}

@Composable
private fun BoxScope.LiquidGlassShaderLayer(
    time: Float,
    distortion: Float,
    glow: Float,
    content: (@Composable BoxScope.() -> Unit)?,
) {
    val materialBrush = Brush.linearGradient(
        colors = listOf(
            Color(0x02BDE8FF),
            Color(0x04FFFFFF),
            Color(0x02FFE2D7),
            Color(0x01BDE8FF),
        ),
        start = Offset.Zero,
        end = Offset(1400f, 900f),
    )
    val ambientShade = Brush.verticalGradient(
        colors = listOf(
            Color(0x06050A12),
            Color(0x10081218),
            Color(0x20040A14),
        ),
    )

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Box(modifier = Modifier.matchParentSize()) {
            content?.invoke(this)
            Box(modifier = Modifier.matchParentSize().background(ambientShade))
            Box(modifier = Modifier.matchParentSize().background(materialBrush))
        }
        return
    }

    val shader = remember { RuntimeShader(LIQUID_GLASS_SHADER) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("distortion", distortion)
                shader.setFloatUniform("glow", glow)
                renderEffect = RenderEffect
                    .createRuntimeShaderEffect(shader, "composable")
                    .asComposeRenderEffect()
            },
    ) {
        content?.invoke(this)
        Box(modifier = Modifier.matchParentSize().background(ambientShade))
        Box(modifier = Modifier.matchParentSize().background(materialBrush))
    }
}
