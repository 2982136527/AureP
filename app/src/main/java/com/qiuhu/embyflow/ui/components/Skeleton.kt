package com.qiuhu.embyflow.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val SkeletonBase = Color(0xFFD0D7E0)
private val SkeletonHighlight = Color(0xFFE8ECF1)

@Composable
fun Modifier.skeletonPlaceholder(
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
): Modifier {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeletonShimmer",
    )
    return this
        .clip(shape)
        .background(SkeletonBase)
        .drawBehind {
            val offset = progress * size.width * 1.5f - size.width * 0.5f
            val brush = Brush.linearGradient(
                colors = listOf(SkeletonBase, SkeletonHighlight, SkeletonBase),
                start = Offset(offset, 0f),
                end = Offset(offset + size.width, size.height),
            )
            drawRect(brush = brush)
        }
}

@Composable
fun SkeletonText(
    lines: Int = 1,
    lineHeight: Dp = 14.dp,
    lineWidths: List<Float> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(lines) { index ->
            val fraction = lineWidths.getOrElse(index) { if (index == lines - 1) 0.6f else 1f }
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(lineHeight)
                    .skeletonPlaceholder(RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
fun SkeletonPoster(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f,
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .skeletonPlaceholder(RoundedCornerShape(16.dp)),
    )
}

@Composable
fun SkeletonBackdrop(
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9f,
) {
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .skeletonPlaceholder(RoundedCornerShape(20.dp)),
    )
}

@Composable
fun HomeScreenSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SkeletonBackdrop(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(236.dp),
        )

        repeat(2) {
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(18.dp)
                        .skeletonPlaceholder(RoundedCornerShape(4.dp)),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(3) {
                        SkeletonPoster(
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreenSkeleton(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(68.dp)
                        .skeletonPlaceholder(RoundedCornerShape(16.dp)),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 16.dp)
                .background(SoftUiSurfacePressed),
        )

        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                SkeletonPoster(
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                SkeletonPoster(
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                SkeletonPoster(
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
