package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val EditorialBackground = SoftUiBackground
val EditorialSurface = SoftUiSurface
val EditorialSurfaceStrong = SoftUiSurfaceRaised
val EditorialTextPrimary = SoftUiTextPrimary
val EditorialTextSecondary = SoftUiTextSecondary
val EditorialShadow = SoftUiShadowDark
val EditorialChip = SoftUiSurfacePressed
val EditorialAccent = SoftUiAccent

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    color: Color = EditorialSurface,
    onClick: (() -> Unit)? = null,
    surfaceStyle: SoftUiSurfaceStyle = SoftUiSurfaceStyle.Raised,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val resolvedStyle = if (pressed && onClick != null) SoftUiSurfaceStyle.Inset else surfaceStyle
    val resolvedColor = if (pressed && onClick != null) softUiPressedColor(color) else color

    Box(
        modifier = modifier
            .pressScale(
                interactionSource = interactionSource,
                enabled = onClick != null,
            )
            .softUiSurface(
                shape = shape,
                style = resolvedStyle,
                color = resolvedColor,
            )
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
        Box(
            modifier = Modifier
                .padding(contentPadding),
        ) {
            content()
        }
    }
}

@Composable
fun EditorialIconButton(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    sizeModifier: Modifier = Modifier,
    color: Color = EditorialSurface,
    onClick: (() -> Unit)? = null,
    surfaceStyle: SoftUiSurfaceStyle = SoftUiSurfaceStyle.Raised,
) {
    EditorialCard(
        modifier = modifier,
        shape = shape,
        color = color,
        onClick = onClick,
        surfaceStyle = surfaceStyle,
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier = sizeModifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = EditorialTextPrimary,
            )
        }
    }
}

@Composable
fun EditorialPill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = EditorialChip,
    surfaceStyle: SoftUiSurfaceStyle = SoftUiSurfaceStyle.Raised,
) {
    Box(
        modifier = modifier
            .softUiSurface(
                shape = RoundedCornerShape(16.dp),
                style = surfaceStyle,
                color = color,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = EditorialTextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
