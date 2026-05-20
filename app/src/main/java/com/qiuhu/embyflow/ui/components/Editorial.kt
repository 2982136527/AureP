package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val EditorialBackground = Color(0xFFF5F1EA)
val EditorialSurface = Color(0xFFFBF7F1)
val EditorialSurfaceStrong = Color(0xFFF0E7DA)
val EditorialTextPrimary = Color(0xFF161310)
val EditorialTextSecondary = Color(0xFF6F685F)
val EditorialShadow = Color(0x22000000)
val EditorialChip = Color(0xFFE8DECF)
val EditorialAccent = Color(0xFF76C85F)

@Composable
fun EditorialCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    color: Color = EditorialSurface,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .pressScale(
                interactionSource = interactionSource,
                enabled = onClick != null,
            )
            .shadow(16.dp, shape, clip = false, ambientColor = EditorialShadow, spotColor = EditorialShadow)
            .clip(shape)
            .background(color)
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
) {
    EditorialCard(
        modifier = modifier,
        shape = shape,
        color = color,
        onClick = onClick,
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
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
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
