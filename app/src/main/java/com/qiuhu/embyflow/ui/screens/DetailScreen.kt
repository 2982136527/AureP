package com.qiuhu.embyflow.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.MediaPerson
import com.qiuhu.embyflow.model.MediaTag
import com.qiuhu.embyflow.model.cardEpisodeBadgeLabel
import com.qiuhu.embyflow.model.detailTags
import com.qiuhu.embyflow.model.isSeries
import com.qiuhu.embyflow.model.seasonEpisodeLabel
import com.qiuhu.embyflow.ui.SeriesDetailState
import com.qiuhu.embyflow.ui.components.EditorialBackground
import com.qiuhu.embyflow.ui.components.EditorialCard
import com.qiuhu.embyflow.ui.components.EditorialIconButton
import com.qiuhu.embyflow.ui.components.EditorialSurface
import com.qiuhu.embyflow.ui.components.EditorialSurfaceStrong
import com.qiuhu.embyflow.ui.components.EditorialTextPrimary
import com.qiuhu.embyflow.ui.components.EditorialTextSecondary
import com.qiuhu.embyflow.ui.components.MediaPosterCard
import com.qiuhu.embyflow.ui.components.MediaPosterCardStyle
import com.qiuhu.embyflow.ui.components.PixelCatAsyncImage
import com.qiuhu.embyflow.ui.components.SoftUiSurfacePressed
import com.qiuhu.embyflow.ui.components.SoftUiSurfaceStyle
import com.qiuhu.embyflow.ui.components.softUiSurface
import com.qiuhu.embyflow.ui.theme.AppTitleFontFamily
import kotlin.math.abs

@Composable
fun DetailScreen(
    media: MediaItem,
    seriesDetail: SeriesDetailState?,
    relatedItems: List<MediaItem>,
    onPlay: (MediaItem) -> Unit,
    onBack: () -> Unit,
    onOpenRelated: (MediaItem) -> Unit,
    onOpenTag: (MediaTag) -> Unit,
    onOpenActor: (MediaPerson) -> Unit,
    onSelectSeason: (String) -> Unit,
    onPlayEpisode: (MediaItem) -> Unit,
) {
    var summaryDialogVisible by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = summaryDialogVisible) {
        summaryDialogVisible = false
    }
    val seriesEpisodes = seriesDetail?.episodes.orEmpty()
    val resumeEpisodeTarget = remember(media, seriesEpisodes) {
        if (!media.isSeries || media.resumePositionMs <= 0L || seriesEpisodes.isEmpty()) {
            null
        } else {
            seriesEpisodes.firstOrNull { episode ->
                val sameSeason = when {
                    !media.seasonId.isNullOrBlank() -> media.seasonId == episode.seasonId
                    media.seasonNumber != null -> media.seasonNumber == episode.seasonNumber
                    else -> true
                }
                val sameEpisode = when {
                    media.episodeNumber != null -> media.episodeNumber == episode.episodeNumber
                    else -> true
                }
                sameSeason && sameEpisode
            }?.let { matchedEpisode ->
                matchedEpisode.copy(
                    resumePositionMs = maxOf(
                        media.resumePositionMs,
                        matchedEpisode.resumePositionMs,
                    ),
                )
            }
        }
    }
    val defaultSeriesStartEpisode = remember(seriesEpisodes, seriesDetail) {
        seriesEpisodes.firstOrNull() ?: seriesDetail?.nextUpEpisode
    }
    val seriesHasWatchHistory = remember(media, seriesDetail, resumeEpisodeTarget) {
        if (!media.isSeries) {
            false
        } else {
            val hasResumeCheckpoint = media.resumePositionMs > 0L || (resumeEpisodeTarget?.resumePositionMs ?: 0L) > 0L
            val hasWatchedEpisodeCount = media.childCount?.let { totalCount ->
                media.unplayedItemCount?.let { unplayedCount ->
                    totalCount > 0 && unplayedCount in 0 until totalCount
                }
            } ?: false
            val nextUpSuggestsProgress = seriesDetail?.nextUpEpisode?.let { nextUp ->
                nextUp.resumePositionMs > 0L ||
                    (nextUp.seasonNumber ?: 1) > 1 ||
                    (nextUp.episodeNumber ?: 1) > 1
            } ?: false
            hasResumeCheckpoint || hasWatchedEpisodeCount || nextUpSuggestsProgress
        }
    }
    val playTarget = when {
        media.isSeries && seriesHasWatchHistory ->
            resumeEpisodeTarget ?: seriesDetail?.nextUpEpisode ?: defaultSeriesStartEpisode ?: media

        media.isSeries -> defaultSeriesStartEpisode ?: media
        else -> media
    }
    val playButtonLabel = when {
        media.isSeries && seriesHasWatchHistory && playTarget.mediaType.equals("Episode", ignoreCase = true) ->
            "继续播放 ${playTarget.seasonEpisodeLabel().ifBlank { "当前这一集" }}"
        media.resumePositionMs > 0L -> "继续播放"
        media.isSeries -> "从第一集开始"
        else -> "开始播放"
    }
    val highlightedEpisodeId = if (seriesHasWatchHistory) {
        resumeEpisodeTarget?.id ?: seriesDetail?.nextUpEpisode?.id
    } else {
        null
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val defaultHeroTransitionDistancePx = with(density) { 280.dp.toPx() }
    var heroCollapsedOffsetPx by rememberSaveable { mutableStateOf(0f) }
    var heroTransitionDistancePx by rememberSaveable { mutableStateOf(0f) }
    val effectiveHeroTransitionDistancePx =
        if (heroTransitionDistancePx > 1f) heroTransitionDistancePx else defaultHeroTransitionDistancePx
    val heroTransitionProgress by remember(heroCollapsedOffsetPx, effectiveHeroTransitionDistancePx) {
        derivedStateOf {
            (heroCollapsedOffsetPx / effectiveHeroTransitionDistancePx).coerceIn(0f, 1f)
        }
    }
    val heroScrollConnection = remember(listState, effectiveHeroTransitionDistancePx) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (available.y < 0f && heroCollapsedOffsetPx < effectiveHeroTransitionDistancePx) {
                    val consume = (-available.y).coerceAtMost(effectiveHeroTransitionDistancePx - heroCollapsedOffsetPx)
                    heroCollapsedOffsetPx += consume
                    return Offset(0f, -consume)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val listAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (available.y > 0f && listAtTop && heroCollapsedOffsetPx > 0f) {
                    val consume = available.y.coerceAtMost(heroCollapsedOffsetPx)
                    heroCollapsedOffsetPx -= consume
                    return Offset(0f, consume)
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorialBackground),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(heroScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EditorialIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        modifier = Modifier.size(44.dp),
                        onClick = onBack,
                    )
                }
            }

            item {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    DetailHeroCard(
                        media = media,
                        heroTransitionProgress = heroTransitionProgress,
                        onOpenTag = onOpenTag,
                        onHeroTransitionDistanceChanged = { newDistance ->
                            if (abs(heroTransitionDistancePx - newDistance) > 0.5f) {
                                heroTransitionDistancePx = newDistance
                            }
                            if (heroCollapsedOffsetPx > newDistance) {
                                heroCollapsedOffsetPx = newDistance
                            }
                        },
                        modifier = Modifier.requiredWidth(maxWidth + 20.dp),
                    )
                }
            }

            item {
                DetailPlayButton(
                    label = playButtonLabel,
                    onPlay = { onPlay(playTarget) },
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (media.isSeries) {
                        DetailSeriesHeaderSection(
                            seriesDetail = seriesDetail,
                            onSelectSeason = onSelectSeason,
                        )
                        if (seriesEpisodes.isNotEmpty()) {
                            DetailEpisodeRow(
                                episodes = seriesEpisodes,
                                nextUpEpisodeId = highlightedEpisodeId,
                                seriesBackdropImageUrl = media.backdropImageUrl,
                                onPlayEpisode = onPlayEpisode,
                            )
                        }
                    }
                    if (media.meta.isNotBlank()) {
                        Text(
                            text = media.meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = EditorialTextSecondary,
                        )
                    }
                    if (media.detailTags().isNotEmpty()) {
                        DetailTagFlow(
                            tags = media.detailTags(),
                            onOpenTag = onOpenTag,
                        )
                    }
                }
            }

            item {
                val summary = media.summary.takeIf { it.isNotBlank() && it != "暂无简介" }
                if (summary != null) {
                    DetailSummaryCard(
                        summary = summary,
                        onClick = { summaryDialogVisible = true },
                    )
                }
            }

            if (media.actors.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "演员",
                            style = MaterialTheme.typography.titleLarge,
                            color = EditorialTextPrimary,
                            fontWeight = FontWeight.Black,
                        )
                        DetailActorRow(
                            actors = media.actors,
                            onOpenActor = onOpenActor,
                        )
                    }
                }
            }

            if (media.extraFanartUrls.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "剧照",
                            style = MaterialTheme.typography.titleLarge,
                            color = EditorialTextPrimary,
                            fontWeight = FontWeight.Black,
                        )
                        DetailExtraFanartRow(
                            imageUrls = media.extraFanartUrls,
                            title = media.title,
                        )
                    }
                }
            }

            if (relatedItems.isNotEmpty()) {
                item {
                    Text(
                        text = "猜你想看",
                        style = MaterialTheme.typography.headlineSmall,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Black,
                    )
                }

                item {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val cardSpacing = 8.dp
                        val cardWidth = (maxWidth - (cardSpacing * 2)) / 3
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                        ) {
                            items(relatedItems) { item ->
                                MediaPosterCard(
                                    media = item,
                                    modifier = Modifier.width(cardWidth),
                                    compact = true,
                                    style = MediaPosterCardStyle.Library,
                                    topRightLabel = item.cardEpisodeBadgeLabel(),
                                    onClick = { onOpenRelated(item) },
                                )
                            }
                        }
                    }
                }
            }
        }

        val summary = media.summary.takeIf { it.isNotBlank() && it != "暂无简介" }
        if (summaryDialogVisible && summary != null) {
            DetailSummaryDialog(
                title = media.title,
                summary = summary,
                onDismiss = { summaryDialogVisible = false },
            )
        }
    }
}

@Composable
private fun DetailTitleBlock(
    media: MediaItem,
    modifier: Modifier = Modifier,
    textColor: Color = EditorialTextPrimary,
    logoMaxHeight: Int = 94,
) {
    if (media.titleLogoUrl != null) {
        AsyncImage(
            model = media.titleLogoUrl,
            contentDescription = media.title,
            modifier = modifier
                .heightIn(min = 48.dp, max = logoMaxHeight.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
        )
        return
    }

    Text(
        text = media.title,
        modifier = modifier,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = AppTitleFontFamily,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            letterSpacing = 0.2.sp,
        ),
        color = textColor,
        fontWeight = FontWeight.Bold,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailActorRow(
    actors: List<MediaPerson>,
    onOpenActor: (MediaPerson) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = actors.take(12),
            key = { actor -> actor.id.ifBlank { actor.name } },
        ) { actor ->
            DetailActorCard(
                actor = actor,
                onClick = { onOpenActor(actor) },
            )
        }
    }
}

@Composable
private fun DetailActorCard(
    actor: MediaPerson,
    onClick: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier.width(112.dp),
        shape = RoundedCornerShape(24.dp),
        color = EditorialSurface,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.82f)
                    .softUiSurface(
                        shape = RoundedCornerShape(18.dp),
                        style = SoftUiSurfaceStyle.Inset,
                        color = SoftUiSurfacePressed,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (actor.imageUrl != null) {
                    PixelCatAsyncImage(
                        model = actor.imageUrl,
                        contentDescription = actor.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = actor.name.take(1),
                        style = MaterialTheme.typography.headlineSmall,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = actor.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = EditorialTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (actor.role.isNotBlank()) {
                    Text(
                        text = actor.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorialTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailExtraFanartRow(
    imageUrls: List<String>,
    title: String,
) {
    if (imageUrls.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            items = imageUrls,
            key = { url -> url },
        ) { imageUrl ->
            EditorialCard(
                modifier = Modifier
                    .width(220.dp)
                    .aspectRatio(16f / 9f),
                shape = RoundedCornerShape(22.dp),
                color = EditorialSurface,
                contentPadding = PaddingValues(0.dp),
            ) {
                PixelCatAsyncImage(
                    model = imageUrl,
                    contentDescription = "$title 剧照",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun DetailTagFlow(
    tags: List<MediaTag>,
    onOpenTag: (MediaTag) -> Unit,
    chipColor: Color = EditorialSurface,
    textColor: Color = EditorialTextPrimary,
) {
    if (tags.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = tags,
            key = { tag -> "${tag.type}-${tag.label}" },
        ) { tag ->
            DetailTagChip(
                tag = tag,
                chipColor = chipColor,
                textColor = textColor,
                onClick = { onOpenTag(tag) },
            )
        }
    }
}

@Composable
private fun DetailTagChip(
    tag: MediaTag,
    chipColor: Color = EditorialSurface,
    textColor: Color = EditorialTextPrimary,
    onClick: () -> Unit,
) {
    EditorialCard(
        shape = RoundedCornerShape(999.dp),
        color = chipColor,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = tag.label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailHeroCard(
    media: MediaItem,
    heroTransitionProgress: Float,
    onOpenTag: (MediaTag) -> Unit,
    onHeroTransitionDistanceChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    val imageUrl = media.backdropImageUrl ?: media.primaryImageUrl
    val isPosterOnlyHero = media.backdropImageUrl.isNullOrBlank() && !media.primaryImageUrl.isNullOrBlank()
    var naturalAspectRatio by remember(imageUrl) { mutableStateOf(16f / 9f) }
    val initialAspectRatio = 2f / 3f
    val posterOnlyTargetAspectRatio = 16f / 9f
    val safeNaturalAspectRatio = if (isPosterOnlyHero) {
        posterOnlyTargetAspectRatio
    } else {
        naturalAspectRatio.coerceAtLeast(initialAspectRatio)
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val density = LocalDensity.current
        val posterOnlyInitialScale = 1f
        val posterOnlyFinalScale = 1.06f
        val imageScale = if (isPosterOnlyHero) {
            posterOnlyInitialScale + ((posterOnlyFinalScale - posterOnlyInitialScale) * heroTransitionProgress)
        } else {
            1f
        }
        val displayAspectRatio =
            initialAspectRatio + (safeNaturalAspectRatio - initialAspectRatio) * heroTransitionProgress
        val heroTransitionDistancePx = with(density) {
            if (isPosterOnlyHero) {
                val cardWidthPx = maxWidth.toPx()
                val initialHeightPx = cardWidthPx / initialAspectRatio
                val targetHeightPx = cardWidthPx / posterOnlyTargetAspectRatio
                (initialHeightPx - targetHeightPx).coerceAtLeast(1f)
            } else {
                val cardWidthPx = maxWidth.toPx()
                val initialHeightPx = cardWidthPx / initialAspectRatio
                val naturalHeightPx = cardWidthPx / safeNaturalAspectRatio
                (initialHeightPx - naturalHeightPx).coerceAtLeast(1f)
            }
        }

        LaunchedEffect(heroTransitionDistancePx) {
            onHeroTransitionDistanceChanged(heroTransitionDistancePx)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(displayAspectRatio)
                .softUiSurface(
                    shape = shape,
                    style = SoftUiSurfaceStyle.Raised,
                    color = EditorialSurface,
                ),
        ) {
            PixelCatAsyncImage(
                model = imageUrl,
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = imageScale
                        scaleY = imageScale
                    },
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    val width = state.result.drawable.intrinsicWidth
                    val height = state.result.drawable.intrinsicHeight
                    if (width > 0 && height > 0) {
                        naturalAspectRatio = width.toFloat() / height.toFloat()
                    }
                },
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.56f),
                            ),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DetailTitleBlock(
                    media = media,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    textColor = Color.White,
                    logoMaxHeight = 82,
                )
            }
        }
    }
}

@Composable
private fun DetailPlayButton(
    label: String,
    onPlay: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = EditorialSurface,
        onClick = onPlay,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = EditorialTextPrimary,
            )
            Text(
                text = label,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = EditorialTextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DetailSeriesHeaderSection(
    seriesDetail: SeriesDetailState?,
    onSelectSeason: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!seriesDetail?.seasons.isNullOrEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = seriesDetail!!.seasons,
                    key = { season -> season.id },
                ) { season ->
                    DetailSeasonChip(
                        season = season,
                        selected = season.id == seriesDetail.selectedSeasonId,
                        onClick = { onSelectSeason(season.id) },
                    )
                }
            }
        }

        when {
            seriesDetail == null || (seriesDetail.isLoading && seriesDetail.episodes.isEmpty()) -> {
                EditorialCard(
                    color = EditorialSurface,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = "正在整理剧集…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EditorialTextSecondary,
                    )
                }
            }

            seriesDetail.errorMessage != null -> {
                EditorialCard(
                    color = EditorialSurface,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = seriesDetail.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EditorialTextSecondary,
                    )
                }
            }
        }

    }
}

@Composable
private fun DetailEpisodeRow(
    episodes: List<MediaItem>,
    nextUpEpisodeId: String?,
    seriesBackdropImageUrl: String?,
    onPlayEpisode: (MediaItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = episodes,
                key = { episode -> episode.id },
            ) { episode ->
                DetailEpisodeCard(
                    episode = episode,
                    isNextUp = nextUpEpisodeId == episode.id,
                    seriesBackdropImageUrl = seriesBackdropImageUrl,
                    onPlay = { onPlayEpisode(episode) },
                    modifier = Modifier.width(208.dp),
                )
            }
        }
    }
}

@Composable
private fun DetailSeasonChip(
    season: MediaItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    EditorialCard(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) EditorialSurfaceStrong else EditorialSurface,
        surfaceStyle = if (selected) SoftUiSurfaceStyle.Inset else SoftUiSurfaceStyle.Raised,
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = season.title,
            style = MaterialTheme.typography.labelMedium,
            color = EditorialTextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailEpisodeCard(
    episode: MediaItem,
    isNextUp: Boolean,
    seriesBackdropImageUrl: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EditorialCard(
        modifier = modifier,
        color = EditorialSurface,
        onClick = onPlay,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .softUiSurface(
                        shape = RoundedCornerShape(20.dp),
                        style = SoftUiSurfaceStyle.Inset,
                        color = SoftUiSurfacePressed,
                    ),
                contentAlignment = Alignment.BottomStart,
            ) {
                PixelCatAsyncImage(
                    model = episode.primaryImageUrl ?: episode.backdropImageUrl ?: seriesBackdropImageUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                if (isNextUp) {
                    Text(
                        text = "继续看",
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .softUiSurface(
                                shape = RoundedCornerShape(999.dp),
                                style = SoftUiSurfaceStyle.Raised,
                                color = EditorialSurfaceStrong,
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = EditorialTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = EditorialTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episode.seasonEpisodeLabel().ifBlank { episode.subtitle },
                    style = MaterialTheme.typography.bodySmall,
                    color = EditorialTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val description = episode.summary.takeIf { it.isNotBlank() && it != "暂无简介" } ?: episode.meta
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorialTextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSummaryCard(
    summary: String,
    onClick: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = EditorialSurface,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = EditorialTextSecondary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "点击查看完整简介",
                style = MaterialTheme.typography.labelMedium,
                color = EditorialTextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DetailSummaryDialog(
    title: String,
    summary: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        EditorialCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            shape = RoundedCornerShape(30.dp),
            color = EditorialSurface,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = AppTitleFontFamily,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        letterSpacing = 0.1.sp,
                    ),
                    color = EditorialTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyLarge,
                        color = EditorialTextSecondary,
                    )
                }
                EditorialCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(999.dp),
                    color = EditorialSurface,
                    onClick = onDismiss,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "关闭",
                            style = MaterialTheme.typography.titleSmall,
                            color = EditorialTextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = EditorialTextSecondary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = EditorialTextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
