package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.ui.theme.LocalAnimatedVisibilityScope
import com.qiuhu.embyflow.ui.theme.LocalSharedTransitionScope

enum class MediaPosterCardStyle {
    Default,
    Library,
}

const val LibraryPosterAspectRatio = 2f / 3f

@Composable
fun MediaPosterCornerBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .softUiSurface(
                shape = RoundedCornerShape(999.dp),
                style = SoftUiSurfaceStyle.Raised,
                color = SoftUiSurfaceRaised,
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = EditorialTextPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun MediaWatchedOverlay(
    played: Boolean,
    playedPercentage: Float,
    modifier: Modifier = Modifier,
) {
    if (!played && playedPercentage <= 0f) return

    Box(modifier = modifier) {
        if (played) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x15000000)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已看完",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else if (playedPercentage > 0f) {
            val trackColor = Color(0x55000000)
            val progressColor = Color(0xFFB0AEC6)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .drawBehind {
                        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        val diameter = size.minDimension - stroke.width
                        val topLeft = Offset(stroke.width / 2f, stroke.width / 2f)
                        drawArc(
                            color = trackColor,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = stroke,
                        )
                        drawArc(
                            color = progressColor,
                            startAngle = -90f,
                            sweepAngle = playedPercentage * 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = stroke,
                        )
                    }
                    .background(Color(0x99000000), CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun MediaPosterCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    style: MediaPosterCardStyle = MediaPosterCardStyle.Default,
    titleBelow: Boolean = false,
    topRightLabel: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    val posterShape = RoundedCornerShape(if (compact) 11.dp else 13.dp)
    val isLibraryStyle = style == MediaPosterCardStyle.Library
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    if (isLibraryStyle && titleBelow) {
        Column(
            modifier = modifier.then(
                if (onClick != null || onLongClick != null) {
                    Modifier
                        .hapticPressScale(interactionSource)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick ?: {},
                            onLongClick = onLongClick,
                        )
                } else {
                    Modifier
                },
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(LibraryPosterAspectRatio)
                    .heightIn(max = if (compact) 240.dp else 320.dp)
                    .then(
                        if (pressed && onClick != null) {
                            Modifier.softUiSurface(
                                shape = shape,
                                style = SoftUiSurfaceStyle.Inset,
                                color = SoftUiSurfacePressed,
                            )
                        } else {
                            Modifier.softUiRaisedSurface(
                                shape = shape,
                                color = EditorialSurface,
                                shadowRadius = 13.dp,
                                shadowOffset = 5.dp,
                            )
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(posterShape)
                        .background(SoftUiSurfacePressed),
                ) {
                    val sharedScope = LocalSharedTransitionScope.current
                    val animScope = LocalAnimatedVisibilityScope.current
                    val imageModifier = if (sharedScope != null && animScope != null) {
                        with(sharedScope) {
                            Modifier.fillMaxSize().sharedElement(
                                rememberSharedContentState(key = "poster-${media.id}"),
                                animatedVisibilityScope = animScope,
                            )
                        }
                    } else Modifier.fillMaxSize()
                    PixelCatAsyncImage(
                        model = media.primaryImageUrl,
                        contentDescription = media.title,
                        modifier = imageModifier,
                        contentScale = ContentScale.Crop,
                    )
                }

                if (!topRightLabel.isNullOrBlank()) {
                    MediaPosterCornerBadge(
                        text = topRightLabel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }

                MediaWatchedOverlay(
                    played = media.played,
                    playedPercentage = media.playedPercentage,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = EditorialTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.subtitle.ifBlank { media.meta },
                    style = MaterialTheme.typography.labelSmall,
                    color = EditorialTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    EditorialCard(
        modifier = modifier,
        shape = shape,
        onClick = onClick,
        onLongClick = onLongClick,
        contentPadding = PaddingValues(0.dp),
    ) {
        if (isLibraryStyle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        when {
                            isLibraryStyle -> LibraryPosterAspectRatio
                            compact -> 1.18f
                            else -> 0.78f
                        },
                    )
                    .then(
                        if (isLibraryStyle) {
                            Modifier.heightIn(max = if (compact) 240.dp else 320.dp)
                        } else {
                            Modifier
                        }
                    )
                    .background(SoftUiSurfacePressed),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(posterShape)
                        .background(SoftUiSurfacePressed),
                ) {
                    val sharedScope2 = LocalSharedTransitionScope.current
                    val animScope2 = LocalAnimatedVisibilityScope.current
                    val imageModifier2 = if (sharedScope2 != null && animScope2 != null) {
                        with(sharedScope2) {
                            Modifier.fillMaxSize().sharedElement(
                                rememberSharedContentState(key = "poster-${media.id}"),
                                animatedVisibilityScope = animScope2,
                            )
                        }
                    } else Modifier.fillMaxSize()
                    PixelCatAsyncImage(
                        model = media.primaryImageUrl,
                        contentDescription = media.title,
                        modifier = imageModifier2,
                        contentScale = ContentScale.Crop,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                )

                Text(
                    text = media.title,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = if (compact) 10.dp else 14.dp),
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    color = EditorialTextPrimary,
                )

                if (!topRightLabel.isNullOrBlank()) {
                    MediaPosterCornerBadge(
                        text = topRightLabel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }

                MediaWatchedOverlay(
                    played = media.played,
                    playedPercentage = media.playedPercentage,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (compact) 1.18f else 0.78f)
                        .background(SoftUiSurfacePressed),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(posterShape)
                            .background(SoftUiSurfacePressed),
                    ) {
                        val sharedScope3 = LocalSharedTransitionScope.current
                        val animScope3 = LocalAnimatedVisibilityScope.current
                        val imageModifier3 = if (sharedScope3 != null && animScope3 != null) {
                            with(sharedScope3) {
                                Modifier.fillMaxSize().sharedElement(
                                    rememberSharedContentState(key = "poster-${media.id}"),
                                    animatedVisibilityScope = animScope3,
                                )
                            }
                        } else Modifier.fillMaxSize()
                        PixelCatAsyncImage(
                            model = media.primaryImageUrl,
                            contentDescription = media.title,
                            modifier = imageModifier3,
                            contentScale = ContentScale.Crop,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent),
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .softUiSurface(
                                shape = RoundedCornerShape(14.dp),
                                style = SoftUiSurfaceStyle.Raised,
                                color = SoftUiSurfaceRaised,
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = if (media.isFolder) Icons.Rounded.CollectionsBookmark else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = EditorialTextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    MediaWatchedOverlay(
                        played = media.played,
                        playedPercentage = media.playedPercentage,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }

                Column(
                    modifier = Modifier.padding(
                        start = 14.dp,
                        end = 14.dp,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = media.title,
                        style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        maxLines = if (compact) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        color = EditorialTextPrimary,
                    )
                    Text(
                        text = media.subtitle.ifBlank { media.meta },
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorialTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
