package com.qiuhu.embyflow.ui.screens

import android.graphics.drawable.BitmapDrawable
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.imageLoader
import coil.request.ImageRequest
import com.qiuhu.embyflow.model.cardEpisodeBadgeLabel
import com.qiuhu.embyflow.model.MediaItem
import com.qiuhu.embyflow.model.hasBackdropImage
import com.qiuhu.embyflow.model.isEpisode
import com.qiuhu.embyflow.ui.components.EditorialBackground
import com.qiuhu.embyflow.ui.components.EditorialChip
import com.qiuhu.embyflow.ui.components.EditorialSurface
import com.qiuhu.embyflow.ui.components.EditorialSurfaceStrong
import com.qiuhu.embyflow.ui.components.EditorialTextPrimary
import com.qiuhu.embyflow.ui.components.EditorialTextSecondary
import com.qiuhu.embyflow.ui.components.MediaPosterCornerBadge
import com.qiuhu.embyflow.ui.components.PixelCatAsyncImage
import com.qiuhu.embyflow.ui.theme.DynamicAccentColors
import com.qiuhu.embyflow.ui.theme.LocalAnimatedVisibilityScope
import com.qiuhu.embyflow.ui.theme.LocalDynamicAccent
import com.qiuhu.embyflow.ui.theme.LocalSharedTransitionScope
import androidx.palette.graphics.Palette
import com.qiuhu.embyflow.ui.components.hapticPressScale
import com.qiuhu.embyflow.ui.components.softUiRaisedSurface
import com.qiuhu.embyflow.ui.components.softUiSurface
import com.qiuhu.embyflow.ui.components.SoftUiShadowDark
import com.qiuhu.embyflow.ui.components.SoftUiSurfacePressed
import com.qiuhu.embyflow.ui.components.SoftUiSurfaceStyle
import com.qiuhu.embyflow.data.settings.HOME_MODULE_CONTINUE_WATCHING
import com.qiuhu.embyflow.data.settings.HOME_MODULE_HERO
import com.qiuhu.embyflow.data.settings.HOME_MODULE_HIGHLIGHTS
import com.qiuhu.embyflow.data.settings.HOME_MODULE_NEXT_UP
import com.qiuhu.embyflow.data.settings.HOME_MODULE_ORDER_DEFAULT
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val TodayBackground = EditorialBackground
private val TodaySurface = EditorialSurface
private val TodaySurfaceStrong = EditorialSurfaceStrong
private val TodayTextPrimary = EditorialTextPrimary
private val TodayTextSecondary = EditorialTextSecondary
private val TodayCardShadow = SoftUiShadowDark
private val TodayChip = EditorialChip
private val TodayAction = EditorialSurface
private const val TodayHighlightsAutoScrollResumeDelayMillis = 2200L
private const val TodayHighlightsAutoScrollPollingMillis = 120L
private val TodayHighlightsAutoScrollSpeed = 18.dp
private const val TodayHeroPrefetchCount = 6
private const val TodayResumeVisibleCards = 1.5f

@Composable
fun HomeScreen(
    isTabActive: Boolean,
    layoutMode: String,
    showEpisodeTitle: Boolean = true,
    heroItems: List<MediaItem>,
    highlightItems: List<MediaItem>,
    continueWatchingItems: List<MediaItem>,
    nextUpItems: List<MediaItem>,
    homeModuleOrder: List<String> = HOME_MODULE_ORDER_DEFAULT,
    homeModuleHidden: Set<String> = emptySet(),
    libraries: List<MediaItem>,
    isServerConnected: Boolean,
    hasConfiguredServer: Boolean,
    onAccentColorChanged: (Color) -> Unit = {},
    onOpenMedia: (MediaItem) -> Unit,
    onOpenLibrary: (MediaItem) -> Unit,
) {
    val homeListState = rememberLazyListState()
    val heroRotationPool = remember(heroItems) {
        heroItems
            .distinctBy { it.id }
            .filter { it.hasBackdropImage() }
    }
    val isLargeLayout = layoutMode == "大图优先"
    val isCompactLayout = layoutMode == "紧凑信息流"
    val highlights = remember(highlightItems) {
        highlightItems.take(8)
    }
    val resumeItems = remember(continueWatchingItems, isCompactLayout, isLargeLayout) {
        continueWatchingItems
            .distinctBy { it.continueWatchingDisplayKey() }
            .take(
                when {
                    isCompactLayout -> 8
                    isLargeLayout -> 4
                    else -> 6
                },
            )
    }
    val nextUpDisplayItems = remember(nextUpItems) {
        nextUpItems.distinctBy { it.seriesId ?: it.id }.take(12)
    }
    val heroWidthFraction = when {
        isCompactLayout -> 0.72f
        isLargeLayout -> 1f
        else -> 0.9f
    }
    val heroCardHeight = when {
        isCompactLayout -> 200.dp
        isLargeLayout -> 276.dp
        else -> 236.dp
    }
    val resumeCardHeightRatio = when {
        isCompactLayout -> 188f / 236f
        isLargeLayout -> 256f / 344f
        else -> 220f / 292f
    }
    val libraryPreviewCount = when {
        isCompactLayout -> 6
        isLargeLayout -> 4
        else -> 5
    }
    val rowSpacing = when {
        isCompactLayout -> 12.dp
        isLargeLayout -> 20.dp
        else -> 16.dp
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TodayBackground),
    ) {
        LazyColumn(
            state = homeListState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(top = 18.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!isServerConnected) {
                item(
                    key = "home-server-state",
                    contentType = "info",
                ) {
                    TodayInfoCard(
                        title = if (hasConfiguredServer) "还没有连接到媒体服务器" else "还没有添加服务器",
                        description = if (hasConfiguredServer) {
                            "检查服务器地址、账号、密码或当前网络后，再回来刷新首页。"
                        } else {
                            "前往设置添加 Emby 服务器后，这里会显示首页推荐、继续观看和媒体库。"
                        },
                    )
                }
                if (hasConfiguredServer) {
                    item(
                        key = "home-server-reconnect",
                        contentType = "info",
                    ) {
                        TodayInfoCard(
                            title = "已保存服务器，等待重新连接",
                            description = "设置页里可以切换其他服务器，或者回到当前服务器重新建立连接。",
                        )
                    }
                }
            } else {
                val visibleModules = homeModuleOrder.filter { it !in homeModuleHidden }

                visibleModules.forEach { moduleId ->
                    when (moduleId) {
                        HOME_MODULE_HERO -> {
                            item(
                                key = "home-hero",
                                contentType = "hero",
                            ) {
                                TodayHeroCarousel(
                                    isTabActive = isTabActive,
                                    items = heroRotationPool,
                                    cardWidthFraction = heroWidthFraction,
                                    cardHeight = heroCardHeight,
                                    onAccentColorChanged = onAccentColorChanged,
                                    onOpenMedia = onOpenMedia,
                                )
                            }
                        }

                        HOME_MODULE_HIGHLIGHTS -> {
                            if (highlights.isNotEmpty()) {
                                item(
                                    key = "home-highlights-header",
                                    contentType = "section-header",
                                ) {
                                    TodaySectionHeader(
                                        title = "今日活动进行时",
                                    )
                                }

                                item(
                                    key = "home-highlights-row",
                                    contentType = "highlights",
                                ) {
                                    TodayHighlightsRow(
                                        isTabActive = isTabActive,
                                        items = highlights,
                                        itemSpacing = 8.dp,
                                        onOpenMedia = onOpenMedia,
                                    )
                                }
                            }
                        }

                        HOME_MODULE_NEXT_UP -> {
                            if (nextUpDisplayItems.isNotEmpty()) {
                                item(
                                    key = "home-nextup-header",
                                    contentType = "section-header",
                                ) {
                                    TodaySectionHeader(
                                        title = "继续播放",
                                    )
                                }

                                item(
                                    key = "home-nextup-row",
                                    contentType = "resume",
                                ) {
                                    TodayContinueWatchingRow(
                                        items = nextUpDisplayItems,
                                        itemSpacing = rowSpacing,
                                        cardHeightRatio = resumeCardHeightRatio,
                                        showEpisodeTitle = showEpisodeTitle,
                                        onOpenMedia = onOpenMedia,
                                    )
                                }
                            }
                        }

                        HOME_MODULE_CONTINUE_WATCHING -> {
                            item(
                                key = "home-resume-header",
                                contentType = "section-header",
                            ) {
                                TodaySectionHeader(
                                    title = "继续观看",
                                )
                            }

                            if (resumeItems.isEmpty()) {
                                item(
                                    key = "home-resume-empty",
                                    contentType = "info",
                                ) {
                                    TodayInfoCard(
                                        title = "还没有继续观看内容",
                                        description = "开始播放后，断点续播会出现在这里。",
                                    )
                                }
                            } else {
                                item(
                                    key = "home-resume-row",
                                    contentType = "resume",
                                ) {
                                    TodayContinueWatchingRow(
                                        items = resumeItems,
                                        itemSpacing = rowSpacing,
                                        cardHeightRatio = resumeCardHeightRatio,
                                        showEpisodeTitle = showEpisodeTitle,
                                        onOpenMedia = onOpenMedia,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayContinueWatchingRow(
    items: List<MediaItem>,
    itemSpacing: Dp,
    cardHeightRatio: Float,
    showEpisodeTitle: Boolean,
    onOpenMedia: (MediaItem) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val horizontalPadding = 16.dp
        val visibleGapCount = (TodayResumeVisibleCards - 0.5f).coerceAtLeast(0f)
        val cardWidth = ((maxWidth - (horizontalPadding * 2) - (itemSpacing * visibleGapCount)) / TodayResumeVisibleCards)
        val cardHeight = cardWidth * cardHeightRatio

        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            items(
                items = items,
                key = { item -> item.continueWatchingDisplayKey() },
                contentType = { "resume-card" },
            ) { item ->
                TodayFeatureCard(
                    media = item,
                    modifier = Modifier.width(cardWidth),
                    height = cardHeight,
                    imageUrlOverride = item.continueWatchingCardImageUrl(),
                    transparentFooter = true,
                    preferTitleLogo = true,
                    hideEpisodeSubtitle = !showEpisodeTitle,
                    topLeftLabel = item.continueWatchingResumeLabel(),
                    plainTopLabels = true,
                    onClick = { onOpenMedia(item.continueWatchingNavigationTarget()) },
                )
            }
        }
    }
}

private fun MediaItem.continueWatchingNavigationTarget(): MediaItem {
    val targetSeriesId = seriesId?.takeIf { isEpisode && it.isNotBlank() } ?: return this
    return copy(
        id = targetSeriesId,
        title = seriesName.ifBlank { title },
        subtitle = "",
        meta = "",
        summary = "",
        score = "",
        primaryImageUrl = seriesPrimaryImageUrl ?: primaryImageUrl,
        backdropImageUrl = seriesBackdropImageUrl ?: backdropImageUrl,
        titleLogoUrl = seriesTitleLogoUrl ?: titleLogoUrl,
        seriesTitleLogoUrl = null,
        mediaType = "Series",
        seriesId = null,
        seriesName = "",
        childCount = null,
        unplayedItemCount = null,
    )
}

private fun MediaItem.continueWatchingCardImageUrl(): String? {
    return if (isEpisode) {
        primaryImageUrl ?: seriesBackdropImageUrl ?: backdropImageUrl
    } else {
        backdropImageUrl ?: primaryImageUrl
    }
}

private fun MediaItem.continueWatchingResumeLabel(): String? {
    val positionMs = resumePositionMs.coerceAtLeast(0L)
    return if (positionMs > 0L) {
        "看到 ${formatResumeTime(positionMs)}"
    } else {
        null
    }
}

private fun MediaItem.continueWatchingDisplayKey(): String = when {
    !seriesId.isNullOrBlank() -> "series:$seriesId"
    isEpisode -> "series:${seriesName.takeIf { it.isNotBlank() } ?: id}"
    else -> "item:$id"
}

private fun formatResumeTime(valueMs: Long): String {
    val totalSeconds = (valueMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun TodayHighlightsRow(
    isTabActive: Boolean,
    items: List<MediaItem>,
    itemSpacing: Dp,
    onOpenMedia: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    val canAutoScroll = items.size > 1
    val density = LocalDensity.current
    val autoScrollSpeedPx = with(density) { TodayHighlightsAutoScrollSpeed.toPx() }
    val initialIndex = remember(items) {
        if (!canAutoScroll) {
            0
        } else {
            val midpoint = Int.MAX_VALUE / 2
            midpoint - (midpoint % items.size)
        }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val interactionScope = rememberCoroutineScope()
    var lastUserInteractionAt by remember(items) { mutableLongStateOf(SystemClock.uptimeMillis()) }
    var isAutoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(canAutoScroll, listState, isTabActive) {
        if (!canAutoScroll || !isTabActive) return@LaunchedEffect
        while (isActive) {
            if (listState.isScrollInProgress && !isAutoScrolling) {
                lastUserInteractionAt = SystemClock.uptimeMillis()
            }
            delay(TodayHighlightsAutoScrollPollingMillis)
        }
    }

    LaunchedEffect(canAutoScroll, listState, lastUserInteractionAt, autoScrollSpeedPx, isTabActive) {
        if (!canAutoScroll || !isTabActive) return@LaunchedEffect
        val idleDuration = SystemClock.uptimeMillis() - lastUserInteractionAt
        val remainingDelay = TodayHighlightsAutoScrollResumeDelayMillis - idleDuration
        if (remainingDelay > 0L) {
            delay(remainingDelay)
        }

        isAutoScrolling = true
        try {
            listState.scroll {
                var previousFrameNanos = withFrameNanos { it }
                while (isActive) {
                    val frameNanos = withFrameNanos { it }
                    val deltaSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
                    previousFrameNanos = frameNanos
                    scrollBy(autoScrollSpeedPx * deltaSeconds)
                }
            }
        } finally {
            isAutoScrolling = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val itemWidth = (maxWidth - (itemSpacing * 2)) / 3
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        ) {
            if (canAutoScroll) {
                items(
                    count = Int.MAX_VALUE,
                    key = { it },
                    contentType = { "highlight-card" },
                ) { index ->
                    val item = items[index % items.size]
                    TodayPosterFeatureCard(
                        media = item,
                        modifier = Modifier.width(itemWidth),
                        onPress = {
                            lastUserInteractionAt = SystemClock.uptimeMillis()
                            interactionScope.launch {
                                listState.stopScroll()
                            }
                        },
                        onClick = { onOpenMedia(item) },
                    )
                }
            } else {
                items(
                    items = items,
                    key = { item -> item.id },
                    contentType = { "highlight-card" },
                ) { item ->
                    TodayPosterFeatureCard(
                        media = item,
                        modifier = Modifier.width(itemWidth),
                        onPress = {
                            lastUserInteractionAt = SystemClock.uptimeMillis()
                        },
                        onClick = { onOpenMedia(item) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TodayPosterFeatureCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val imageUrl = media.primaryImageUrl ?: media.backdropImageUrl
    val badgeLabel = media.cardEpisodeBadgeLabel()
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .hapticPressScale(interactionSource)
            .pointerInput(onPress) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .softUiRaisedSurface(
                    shape = shape,
                    color = TodaySurface,
                    shadowRadius = 18.dp,
                    shadowOffset = 8.dp,
                ),
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
                model = imageUrl,
                contentDescription = media.title,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
            )

            if (!badgeLabel.isNullOrBlank()) {
                MediaPosterCornerBadge(
                    text = badgeLabel,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
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
                color = TodayTextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = media.subtitle.ifBlank { media.meta },
                style = MaterialTheme.typography.labelSmall,
                color = TodayTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayHeroCarousel(
    isTabActive: Boolean,
    items: List<MediaItem>,
    cardWidthFraction: Float,
    cardHeight: Dp,
    onAccentColorChanged: (Color) -> Unit = {},
    onOpenMedia: (MediaItem) -> Unit,
) {
    if (items.isEmpty()) return

    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val density = LocalDensity.current
    var rotationOrder by remember(items) {
        mutableStateOf(items.shuffledForHeroRotation())
    }
    var activeIndex by remember(items) {
        mutableIntStateOf(0)
    }
    var warmedHeroUrls by remember(items) { mutableStateOf(setOf<String>()) }
    val activeItem = rotationOrder.getOrElse(activeIndex) { items.first() }

    LaunchedEffect(items, isTabActive) {
        if (!isTabActive) return@LaunchedEffect
        rotationOrder = items.shuffledForHeroRotation()
        activeIndex = 0
        while (true) {
            delay(4800)
            if (rotationOrder.size <= 1) continue

            val nextIndex = activeIndex + 1
            if (nextIndex < rotationOrder.size) {
                activeIndex = nextIndex
            } else {
                val previousItemId = rotationOrder
                    .getOrNull(activeIndex)
                    ?.id
                    ?: items.firstOrNull()?.id
                rotationOrder = items.shuffledForHeroRotation(previousId = previousItemId)
                activeIndex = 0
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
    ) {
        val targetWidthPx = with(density) { (maxWidth * cardWidthFraction).roundToPx().coerceAtLeast(1) }
        val targetHeightPx = with(density) { cardHeight.roundToPx().coerceAtLeast(1) }

        LaunchedEffect(rotationOrder, activeIndex, targetWidthPx, targetHeightPx, isTabActive) {
            if (!isTabActive) return@LaunchedEffect
            val upcomingUrls = buildList {
                val limit = minOf(TodayHeroPrefetchCount, rotationOrder.size)
                repeat(limit) { offset ->
                    val media = rotationOrder.getOrNull((activeIndex + offset) % rotationOrder.size)
                    val imageUrl = media?.backdropImageUrl ?: media?.primaryImageUrl
                    if (!imageUrl.isNullOrBlank()) {
                        add(imageUrl)
                    }
                }
            }.filterNot { warmedHeroUrls.contains(it) }

            if (upcomingUrls.isEmpty()) return@LaunchedEffect

            upcomingUrls.forEach { imageUrl ->
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .size(targetWidthPx, targetHeightPx)
                        .memoryCacheKey(imageUrl)
                        .diskCacheKey(imageUrl)
                        .build(),
                )
            }
            warmedHeroUrls = warmedHeroUrls + upcomingUrls
        }

        AnimatedContent(
            targetState = activeItem,
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(
                            durationMillis = 650,
                            easing = FastOutSlowInEasing,
                        ),
                    ) +
                        slideInHorizontally(
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = FastOutSlowInEasing,
                            ),
                            initialOffsetX = { it / 16 },
                        ) +
                        scaleIn(
                            animationSpec = tween(
                                durationMillis = 650,
                                easing = FastOutSlowInEasing,
                            ),
                            initialScale = 0.985f,
                        )
                    ) togetherWith (
                    fadeOut(
                        animationSpec = tween(
                            durationMillis = 520,
                            easing = FastOutSlowInEasing,
                        ),
                    ) +
                        slideOutHorizontally(
                            animationSpec = tween(
                                durationMillis = 520,
                                easing = FastOutSlowInEasing,
                            ),
                            targetOffsetX = { -it / 18 },
                        ) +
                        scaleOut(
                            animationSpec = tween(
                                durationMillis = 520,
                                easing = FastOutSlowInEasing,
                            ),
                            targetScale = 1.015f,
                        )
                    )
            },
            label = "today-hero-rotation",
        ) { item ->
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                TodayFeatureCard(
                    media = item,
                    modifier = Modifier.fillMaxWidth(cardWidthFraction),
                    height = cardHeight,
                    imageContentScale = ContentScale.Crop,
                    transparentFooter = true,
                    preferTitleLogo = true,
                    onAccentColorChanged = onAccentColorChanged,
                    onClick = { onOpenMedia(item) },
                )
            }
        }
    }
}

@Composable
private fun TodaySectionHeader(
    title: String,
    subtitle: String = "",
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TodayTextPrimary,
            fontWeight = FontWeight.Black,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TodayTextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun TodayFeatureCard(
    media: MediaItem,
    modifier: Modifier = Modifier,
    height: Dp,
    imageUrlOverride: String? = null,
    imageContentScale: ContentScale = ContentScale.Crop,
    adaptCardToImage: Boolean = false,
    transparentFooter: Boolean = false,
    preferTitleLogo: Boolean = false,
    hideEpisodeSubtitle: Boolean = false,
    topLeftLabel: String? = null,
    plainTopLabels: Boolean = false,
    onAccentColorChanged: (Color) -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val imageShape = RoundedCornerShape(13.dp)
    val imageUrl = imageUrlOverride ?: (media.backdropImageUrl ?: media.primaryImageUrl)
    val badgeLabel = media.cardEpisodeBadgeLabel()
    val interactionSource = remember { MutableInteractionSource() }
    var imageAspectRatio by remember(imageUrl) { mutableStateOf(16f / 9f) }
    var accentColor by remember(imageUrl) { mutableStateOf<Color?>(null) }
    val cardSizeModifier = if (adaptCardToImage) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(imageAspectRatio)
    } else {
        Modifier
            .fillMaxWidth()
            .height(height)
    }

    Box(
        modifier = modifier
            .then(cardSizeModifier)
            .hapticPressScale(interactionSource)
            .softUiRaisedSurface(
                shape = shape,
                color = TodaySurface,
                shadowRadius = 14.dp,
                shadowOffset = 5.dp,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        val dynamicColors = remember(accentColor) {
            accentColor?.let { c ->
                DynamicAccentColors(
                    primary = c,
                    surfaceTint = c.copy(alpha = 0.12f),
                    heroGradientEnd = c.copy(alpha = 0.7f),
                )
            }
        }
        CompositionLocalProvider(
            *(if (dynamicColors != null) arrayOf(LocalDynamicAccent provides dynamicColors) else emptyArray()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(imageShape)
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
                    model = imageUrl,
                    contentDescription = media.title,
                    modifier = imageModifier2,
                    contentScale = imageContentScale,
                    onSuccess = { state ->
                        if (adaptCardToImage) {
                            val width = state.result.drawable.intrinsicWidth
                            val heightPx = state.result.drawable.intrinsicHeight
                            if (width > 0 && heightPx > 0) {
                                imageAspectRatio = width.toFloat() / heightPx.toFloat()
                            }
                        }
                        val bitmap = (state.result.drawable as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            val safeBitmap = if (bitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                                bitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                            } else bitmap
                            val palette = Palette.from(safeBitmap!!).generate()
                            val rgb = palette.vibrantSwatch?.rgb
                                ?: palette.dominantSwatch?.rgb
                                ?: palette.mutedSwatch?.rgb
                            if (rgb != null) {
                                accentColor = Color(rgb)
                                onAccentColorChanged(Color(rgb))
                            }
                        }
                    },
                )
            }

            if (!topLeftLabel.isNullOrBlank()) {
                if (plainTopLabels) {
                    TodayTopOverlayLabel(
                        text = topLeftLabel,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                    )
                } else {
                    MediaPosterCornerBadge(
                        text = topLeftLabel,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp),
                    )
                }
            }

            if (!badgeLabel.isNullOrBlank()) {
                if (plainTopLabels) {
                    TodayTopOverlayLabel(
                        text = badgeLabel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                    )
                } else {
                    MediaPosterCornerBadge(
                        text = badgeLabel,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                    )
                }
            }

            if (transparentFooter) {
                val gradientEnd = accentColor?.copy(alpha = 0.7f)
                    ?: Color(0xB3000000)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .fillMaxHeight(0.55f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    gradientEnd,
                                ),
                            ),
                        ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 18.dp, top = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                TodayCardFooter(
                    media = media,
                    transparent = transparentFooter,
                    preferTitleLogo = preferTitleLogo,
                    hideEpisodeSubtitle = hideEpisodeSubtitle,
                    topLeftLabel = topLeftLabel,
                )
            }
        }
    }
}

@Composable
private fun TodayTopOverlayLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .softUiSurface(
                shape = RoundedCornerShape(999.dp),
                style = SoftUiSurfaceStyle.Raised,
                color = TodaySurfaceStrong,
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = TodayTextPrimary,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
    )
}

@Composable
private fun TodayCardFooter(
    media: MediaItem,
    compact: Boolean = false,
    transparent: Boolean = false,
    preferTitleLogo: Boolean = false,
    hideEpisodeSubtitle: Boolean = false,
    topLeftLabel: String? = null,
) {
    val logoModel = when {
        preferTitleLogo && media.isEpisode -> media.seriesTitleLogoUrl ?: media.titleLogoUrl
        preferTitleLogo -> media.titleLogoUrl
        else -> null
    }
    val shouldUseSurface = !transparent && logoModel.isNullOrBlank()
    val overlayTitleColor = if (transparent) Color.White else TodayTextPrimary
    val overlayMetaColor = if (transparent) Color.White.copy(alpha = 0.82f) else TodayTextSecondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (shouldUseSurface) {
                    Modifier.softUiSurface(
                        shape = RoundedCornerShape(if (compact) 14.dp else 16.dp),
                        style = SoftUiSurfaceStyle.Inset,
                        color = TodaySurfaceStrong,
                    )
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 7.dp else 8.dp,
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (preferTitleLogo && media.isEpisode) {
                val primaryEpisodeTitle = media.seriesName.ifBlank { media.title }
                val secondaryEpisodeTitle = media.title.takeIf {
                    it.isNotBlank() && it != primaryEpisodeTitle
                }
                if (!logoModel.isNullOrBlank()) {
                    TodayCardLogo(
                        model = logoModel,
                        contentDescription = primaryEpisodeTitle,
                        compact = compact,
                    )
                } else {
                    Text(
                        text = primaryEpisodeTitle,
                        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                        color = overlayTitleColor,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!hideEpisodeSubtitle) {
                    secondaryEpisodeTitle?.let { episodeTitle ->
                        Text(
                            text = episodeTitle,
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                            color = overlayTitleColor,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                TodayCardTitle(
                    media = media,
                    compact = compact,
                    preferTitleLogo = preferTitleLogo,
                    textColor = overlayTitleColor,
                )
            }
            if (topLeftLabel.isNullOrBlank()) {
                Text(
                    text = media.subtitle.ifBlank { media.meta },
                    style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                    color = overlayMetaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TodayCardTitle(
    media: MediaItem,
    compact: Boolean = false,
    preferTitleLogo: Boolean = false,
    textColor: Color = TodayTextPrimary,
) {
    if (preferTitleLogo && media.titleLogoUrl != null) {
        TodayCardLogo(
            model = media.titleLogoUrl,
            contentDescription = media.title,
            compact = compact,
            fallbackTextColor = textColor,
        )
        return
    }

    Text(
        text = media.title,
        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        color = textColor,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TodayCardLogo(
    model: Any?,
    contentDescription: String?,
    compact: Boolean,
    fallbackTextColor: Color = TodayTextPrimary,
) {
    SubcomposeAsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxWidth(if (compact) 0.5f else 0.55f)
            .heightIn(
                min = if (compact) 14.dp else 18.dp,
                max = if (compact) 20.dp else 24.dp,
            ),
        contentScale = ContentScale.Fit,
        alignment = Alignment.CenterStart,
        loading = {},
        success = { SubcomposeAsyncImageContent() },
        error = {
            Text(
                text = contentDescription.orEmpty(),
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                color = fallbackTextColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun TodayInfoCard(
    title: String,
    description: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .softUiRaisedSurface(
                shape = RoundedCornerShape(28.dp),
                color = TodaySurface,
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TodaySurfaceStrong),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = TodayTextPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TodayTextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TodayTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun TodayLibraryRow(
    media: MediaItem,
    onClick: () -> Unit,
) {
    val imageUrl = media.backdropImageUrl ?: media.primaryImageUrl
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .hapticPressScale(interactionSource)
            .softUiRaisedSurface(
                shape = RoundedCornerShape(24.dp),
                color = TodaySurface,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(116.dp)
                    .aspectRatio(16f / 9f)
                    .softUiSurface(
                        shape = RoundedCornerShape(14.dp),
                        style = SoftUiSurfaceStyle.Inset,
                        color = SoftUiSurfacePressed,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                PixelCatAsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = media.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TodayTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = media.summary.ifBlank { media.subtitle },
                    style = MaterialTheme.typography.bodySmall,
                    color = TodayTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(TodayChip)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "打开",
                        style = MaterialTheme.typography.labelLarge,
                        color = TodayTextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TodayTextSecondary,
                )
            }
        }
    }
}

private fun List<MediaItem>.pickNextRandom(
    previousId: String,
): MediaItem {
    if (size <= 1) return first()
    val candidates = filterNot { it.id == previousId }
    return if (candidates.isNotEmpty()) candidates.random() else random()
}

private fun List<MediaItem>.shuffledForHeroRotation(
    previousId: String? = null,
): List<MediaItem> {
    if (size <= 1) return this
    val shuffled = shuffled().toMutableList()
    if (previousId == null) {
        return shuffled
    }
    val firstCandidateIndex = shuffled.indexOfFirst { it.id != previousId }
    if (firstCandidateIndex > 0) {
        val firstCandidate = shuffled.removeAt(firstCandidateIndex)
        shuffled.add(0, firstCandidate)
    }
    return shuffled
}
