package com.qiuhu.embyflow.ui.netflix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.qiuhu.embyflow.ui.components.PixelCatAsyncImage

data class NetflixRowSection(
    val title: String,
    val items: List<MediaItem>,
)

@Composable
fun NetflixPrototypeScreen(
    featured: MediaItem,
    continueWatching: List<MediaItem>,
    sections: List<NetflixRowSection>,
    onOpenMedia: (MediaItem) -> Unit,
    onPlayFeatured: () -> Unit = { onOpenMedia(featured) },
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixBackground),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                NetflixHero(
                    media = featured,
                    onOpenMedia = { onOpenMedia(featured) },
                    onPlay = onPlayFeatured,
                )
            }

            if (continueWatching.isNotEmpty()) {
                item {
                    NetflixSection(
                        title = "继续观看",
                        items = continueWatching,
                        onOpenMedia = onOpenMedia,
                    )
                }
            }

            items(sections) { section ->
                NetflixSection(
                    title = section.title,
                    items = section.items,
                    onOpenMedia = onOpenMedia,
                )
            }
        }

        NetflixTopBar(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp),
        )

        NetflixBottomBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun NetflixTopBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AUREP",
            style = MaterialTheme.typography.titleLarge,
            color = NetflixAccent,
            fontWeight = FontWeight.Black,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "剧集",
                style = MaterialTheme.typography.labelLarge,
                color = NetflixTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "电影",
                style = MaterialTheme.typography.labelLarge,
                color = NetflixTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = NetflixTextPrimary,
            )
        }
    }
}

@Composable
private fun NetflixHero(
    media: MediaItem,
    onOpenMedia: () -> Unit,
    onPlay: () -> Unit,
) {
    val imageUrl = media.backdropImageUrl ?: media.primaryImageUrl

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(540.dp)
            .background(Color.Black)
            .clickable(onClick = onOpenMedia),
    ) {
        PixelCatAsyncImage(
            model = imageUrl,
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
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(modifier = Modifier.height(180.dp))
            Text(
                text = media.title,
                style = MaterialTheme.typography.displaySmall,
                color = NetflixTextPrimary,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = media.subtitle.ifBlank { media.meta },
                style = MaterialTheme.typography.bodyMedium,
                color = NetflixTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NetflixActionButton(
                    icon = Icons.Rounded.PlayArrow,
                    text = "播放",
                    background = NetflixTextPrimary,
                    contentColor = Color.Black,
                    onClick = onPlay,
                )
                NetflixActionButton(
                    icon = Icons.Rounded.Add,
                    text = "片单",
                    background = NetflixSurfaceElevated,
                    contentColor = NetflixTextPrimary,
                    onClick = onOpenMedia,
                )
            }
        }
    }
}

@Composable
private fun NetflixActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun NetflixSection(
    title: String,
    items: List<MediaItem>,
    onOpenMedia: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 18.dp),
            style = MaterialTheme.typography.titleLarge,
            color = NetflixTextPrimary,
            fontWeight = FontWeight.Bold,
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items) { item ->
                NetflixPosterCard(
                    media = item,
                    onClick = { onOpenMedia(item) },
                )
            }
        }
    }
}

@Composable
private fun NetflixPosterCard(
    media: MediaItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(12.dp, RoundedCornerShape(6.dp), clip = false)
                .clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(media.colors)),
        ) {
            PixelCatAsyncImage(
                model = media.primaryImageUrl ?: media.backdropImageUrl,
                contentDescription = media.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        Text(
            text = media.title,
            style = MaterialTheme.typography.bodySmall,
            color = NetflixTextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NetflixBottomBar(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NetflixSurface.copy(alpha = 0.94f))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetflixBottomItem(
            icon = Icons.Rounded.Home,
            label = "主页",
            selected = true,
        )
        NetflixBottomItem(
            icon = Icons.Rounded.Search,
            label = "搜索",
            selected = false,
        )
        NetflixBottomItem(
            icon = Icons.Rounded.VideoLibrary,
            label = "片库",
            selected = false,
        )
    }
}

@Composable
private fun NetflixBottomItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) NetflixTextPrimary else NetflixTextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) NetflixTextPrimary else NetflixTextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
