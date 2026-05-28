package com.qiuhu.embyflow.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.ui.theme.LocalDynamicAccent

private val NavSelected = SoftUiSurfacePressed
private val NavIconMuted = SoftUiTextSecondary
val FloatingNavBarHeight = 58.dp
val FloatingNavBarOuterPadding = 12.dp
val FloatingNavBarSheetClearance = 16.dp

data class FloatingNavItem<T>(
    val value: T,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun <T> FloatingNavigationBar(
    modifier: Modifier = Modifier,
    selectedTab: T,
    items: List<FloatingNavItem<T>>,
    searchIcon: ImageVector,
    onTabSelected: (T) -> Unit,
    onSearchClick: () -> Unit,
) {
    val searchInteractionSource = remember { MutableInteractionSource() }
    val accentColor = LocalDynamicAccent.current.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassPanel(
            modifier = Modifier
                .weight(1f)
                .height(FloatingNavBarHeight),
            shape = RoundedCornerShape(29.dp),
            emphasis = GlassEmphasis.Regular,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    NavIconButton(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        selected = item.value == selectedTab,
                        icon = item.icon,
                        label = item.label,
                        accentColor = accentColor,
                        onClick = { onTabSelected(item.value) },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        GlassCircleButton(
            modifier = Modifier.size(FloatingNavBarHeight),
            onClick = onSearchClick,
            emphasis = GlassEmphasis.Strong,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = searchIcon,
                    contentDescription = "Search",
                    tint = accentColor,
                )
            }
        }
    }
}

@Composable
private fun NavIconButton(
    modifier: Modifier,
    selected: Boolean,
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val iconTint = animateColorAsState(
        targetValue = if (selected) accentColor else NavIconMuted,
        animationSpec = tween(durationMillis = 220),
        label = "navIconTint",
    )

    Box(
        modifier = modifier
            .hapticPressScale(interactionSource)
            .softUiSurface(
                shape = RoundedCornerShape(23.dp),
                style = if (selected) SoftUiSurfaceStyle.Inset else SoftUiSurfaceStyle.Raised,
                color = if (selected) NavSelected else Color.Transparent,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint.value,
        )
    }
}
