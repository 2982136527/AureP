package com.qiuhu.embyflow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qiuhu.embyflow.model.MediaItem

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
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFF2E4D3).copy(alpha = 0.94f))
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
fun MediaPosterCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    style: MediaPosterCardStyle = MediaPosterCardStyle.Default,
    titleBelow: Boolean = false,
    topRightLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(if (compact) 14.dp else 16.dp)
    val isLibraryStyle = style == MediaPosterCardStyle.Library
    val interactionSource = remember { MutableInteractionSource() }

    if (isLibraryStyle && titleBelow) {
        Column(
            modifier = modifier.then(
                if (onClick != null) {
                    Modifier
                        .pressScale(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick,
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
                    .shadow(16.dp, shape, clip = false, ambientColor = EditorialShadow, spotColor = EditorialShadow)
                    .clip(shape)
                    .background(Brush.linearGradient(media.colors)),
            ) {
                PixelCatAsyncImage(
                    model = media.primaryImageUrl,
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                if (!topRightLabel.isNullOrBlank()) {
                    MediaPosterCornerBadge(
                        text = topRightLabel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
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
                    .background(Brush.linearGradient(media.colors)),
            ) {
                PixelCatAsyncImage(
                    model = media.primaryImageUrl,
                    contentDescription = media.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color(0x26000000),
                                    Color(0xB0000000),
                                ),
                            ),
                        ),
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
                    color = Color.White,
                )

                if (!topRightLabel.isNullOrBlank()) {
                    MediaPosterCornerBadge(
                        text = topRightLabel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                    )
                }
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (compact) 1.18f else 0.78f)
                        .background(Brush.linearGradient(media.colors)),
                ) {
                    PixelCatAsyncImage(
                        model = media.primaryImageUrl,
                        contentDescription = media.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color(0x12000000),
                                        Color(0x72000000),
                                    ),
                                ),
                            ),
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.86f))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = if (media.isFolder) Icons.Rounded.CollectionsBookmark else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = EditorialTextPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
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
