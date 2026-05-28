package com.qiuhu.embyflow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    pressedAlpha: Float = 0.98f,
): Modifier = composed {
    if (!enabled) {
        return@composed this
    }

    val isPressed by interactionSource.collectIsPressedAsState()
    val targetScale = if (isPressed) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) pressedAlpha else 1f,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressAlpha",
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

fun Modifier.hapticPressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.975f,
    pressedAlpha: Float = 0.98f,
): Modifier = composed {
    if (!enabled) {
        return@composed this
    }

    val isPressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(isPressed) {
        if (isPressed) {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val targetScale = if (isPressed) pressedScale else 1f
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "hapticPressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) pressedAlpha else 1f,
        animationSpec = spring(
            dampingRatio = 0.92f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "hapticPressAlpha",
    )

    graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}
